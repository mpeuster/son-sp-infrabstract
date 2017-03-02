/*
 * Copyright (c) 2015 SONATA-NFV, UCL, NOKIA, NCSR Demokritos ALL RIGHTS RESERVED.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 *
 * Neither the name of the SONATA-NFV, UCL, NOKIA, NCSR Demokritos nor the names of its contributors
 * may be used to endorse or promote products derived from this software without specific prior
 * written permission.
 *
 * This work has been performed in the framework of the SONATA project, funded by the European
 * Commission under Grant number 671517 through the Horizon 2020 and 5G-PPP programmes. The authors
 * would like to acknowledge the contributions of their colleagues of the SONATA partner consortium
 * (www.sonata-nfv.eu).
 *
 * @author Dario Valocchi (Ph.D.), UCL
 *
 */

package sonata.kernel.vimadaptor;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import sonata.kernel.vimadaptor.commons.*;
import sonata.kernel.vimadaptor.commons.nsd.ServiceDescriptor;
import sonata.kernel.vimadaptor.commons.vnfd.Unit;
import sonata.kernel.vimadaptor.commons.vnfd.Unit.MemoryUnit;
import sonata.kernel.vimadaptor.commons.vnfd.UnitDeserializer;
import sonata.kernel.vimadaptor.commons.vnfd.VnfDescriptor;
import sonata.kernel.vimadaptor.messaging.ServicePlatformMessage;
import sonata.kernel.vimadaptor.messaging.TestConsumer;
import sonata.kernel.vimadaptor.messaging.TestProducer;
import sonata.kernel.vimadaptor.wrapper.WrapperConfiguration;
import sonata.kernel.vimadaptor.wrapper.ovsWrapper.OvsWrapper;

import java.io.*;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;


/**
 * Unit test for simple App.
 */

public class DeployServiceEmulatorTest implements MessageReceiver {
  private String output = null;
  private Object mon = new Object();
  private TestConsumer consumer;
  private String lastHeartbeat;
  private ServiceDeployPayload data;
  private ServiceDeployPayload dataV1;
  private ServiceDeployPayload data1V1;
  private ObjectMapper mapper;

  /**
   * Set up the test environment
   *
   */
  @Before
  public void setUp() throws Exception {

    ServiceDescriptor sd;
    StringBuilder bodyBuilder = new StringBuilder();
    BufferedReader in = new BufferedReader(new InputStreamReader(
        new FileInputStream(new File("./YAML/sonata-demo.yml")), Charset.forName("UTF-8")));
    String line;
    while ((line = in.readLine()) != null)
      bodyBuilder.append(line + "\n\r");
    this.mapper = new ObjectMapper(new YAMLFactory());
    SimpleModule module = new SimpleModule();
    module.addDeserializer(Unit.class, new UnitDeserializer());
    mapper.registerModule(module);
    mapper.enable(DeserializationFeature.READ_ENUMS_USING_TO_STRING);
    mapper.disable(SerializationFeature.WRITE_EMPTY_JSON_ARRAYS);
    mapper.enable(SerializationFeature.WRITE_ENUMS_USING_TO_STRING);
    mapper.disable(SerializationFeature.WRITE_NULL_MAP_VALUES);
    mapper.setSerializationInclusion(Include.NON_NULL);

    sd = mapper.readValue(bodyBuilder.toString(), ServiceDescriptor.class);

    VnfDescriptor vnfd1;
    bodyBuilder = new StringBuilder();
    in = new BufferedReader(new InputStreamReader(
        new FileInputStream(new File("./YAML/vtc-vnf-vnfd.yml")), Charset.forName("UTF-8")));
    line = null;
    while ((line = in.readLine()) != null)
      bodyBuilder.append(line + "\n\r");
    vnfd1 = mapper.readValue(bodyBuilder.toString(), VnfDescriptor.class);

    VnfDescriptor vnfd2;
    bodyBuilder = new StringBuilder();
    in = new BufferedReader(new InputStreamReader(
        new FileInputStream(new File("./YAML/fw-vnf-vnfd.yml")), Charset.forName("UTF-8")));
    line = null;
    while ((line = in.readLine()) != null)
      bodyBuilder.append(line + "\n\r");
    vnfd2 = mapper.readValue(bodyBuilder.toString(), VnfDescriptor.class);

    this.data = new ServiceDeployPayload();
    this.data.setServiceDescriptor(sd);
    this.data.addVnfDescriptor(vnfd1);
    this.data.addVnfDescriptor(vnfd2);
  }

  /**
   * This test is de-activated, if you want to use it with your NFVi-PoP, please edit the addVimBody
   * and addNetVimBody String Member to match your OpenStack and ovs configuration and substitute
   * the @Ignore annotation with the @Test annotation
   *
   * @throws Exception
   */
  @Test
  public void testDeployServiceOpenStackV1() throws Exception {

    BlockingQueue<ServicePlatformMessage> muxQueue =
        new LinkedBlockingQueue<ServicePlatformMessage>();
    BlockingQueue<ServicePlatformMessage> dispatcherQueue =
        new LinkedBlockingQueue<ServicePlatformMessage>();

    TestProducer producer = new TestProducer(muxQueue, this);
    consumer = new TestConsumer(dispatcherQueue);
    AdaptorCore core = new AdaptorCore(muxQueue, dispatcherQueue, consumer, producer, 0.1);

    core.start();
    int counter = 0;

    try {
      while (counter < 2) {
        synchronized (mon) {
          mon.wait();
          if (lastHeartbeat.contains("RUNNING")) counter++;
        }
      }
    } catch (Exception e) {
      Assert.assertTrue(false);
    }

    // PoP local emulator
    String addVimBody = "{\"vim_type\":\"Heat\", "
        + "\"tenant_ext_router\":\"0e5d6e42-e544-4ec3-8ce1-9ac950ae994b\", "
        + "\"tenant_ext_net\":\"c999f013-2022-4464-b44f-88f4437f23b0\","
        + "\"vim_address\":\"127.0.0.1\",\"username\":\"username\","
        + "\"pass\":\"password\",\"tenant\":\"tenantName\"}";

    String topic = "infrastructure.management.compute.add";
    ServicePlatformMessage addVimMessage = new ServicePlatformMessage(addVimBody,
        "application/json", topic, UUID.randomUUID().toString(), topic);

    consumer.injectMessage(addVimMessage);
    Thread.sleep(2000);
    while (output == null)
      synchronized (mon) {
        mon.wait(1000);
      }

    JSONTokener tokener = new JSONTokener(output);
    JSONObject jsonObject = (JSONObject) tokener.nextValue();
    String status = jsonObject.getString("status");
    String computeWrUuid = jsonObject.getString("uuid");
    Assert.assertTrue(status.equals("COMPLETED"));
    System.out.println("OpenStack Wrapper added, with uuid: " + computeWrUuid);

    // OpenDaylight integration
    /*
    output = null;
    // PoP .10
    // String addNetVimBody = "{\"vim_type\":\"ovs\", "
    // + "\"vim_address\":\"10.100.32.10\",\"username\":\"operator\","
    // + "\"pass\":\"apass\",\"tenant\":\"tenant\",\"compute_uuid\":\"" + computeWrUuid + "\"}";

    // PoP .200
    String addNetVimBody = "{\"vim_type\":\"ovs\", "
        + "\"vim_address\":\"10.100.32.200\",\"username\":\"operator\","
        + "\"pass\":\"apass\",\"tenant\":\"tenant\",\"compute_uuid\":\"" + computeWrUuid + "\"}";

    topic = "infrastructure.management.network.add";
    ServicePlatformMessage addNetVimMessage = new ServicePlatformMessage(addNetVimBody,
        "application/json", topic, UUID.randomUUID().toString(), topic);
    consumer.injectMessage(addNetVimMessage);
    Thread.sleep(2000);
    while (output == null)
      synchronized (mon) {
        mon.wait(1000);
      }

    tokener = new JSONTokener(output);
    jsonObject = (JSONObject) tokener.nextValue();
    status = null;
    status = jsonObject.getString("status");
    String netWrUuid = jsonObject.getString("uuid");
    Assert.assertTrue("Failed to add the ovs wrapper. Status " + status,
        status.equals("COMPLETED"));
    System.out.println("OpenDaylight Wrapper added, with uuid: " + netWrUuid);
    */

    // deploy service
    output = null;
    String baseInstanceUuid = data.getNsd().getInstanceUuid();
    data.setVimUuid(computeWrUuid);
    data.getNsd().setInstanceUuid(baseInstanceUuid + "-01");

    String body = mapper.writeValueAsString(data);

    topic = "infrastructure.service.deploy";
    ServicePlatformMessage deployServiceMessage = new ServicePlatformMessage(body,
        "application/x-yaml", topic, UUID.randomUUID().toString(), topic);

    /*
    consumer.injectMessage(deployServiceMessage);

    //Thread.sleep(2000);
    //while (output == null)
    //  synchronized (mon) {
    //    mon.wait(1000);
    //  }
    //Assert.assertNotNull(output);
    int retry = 0;
    int maxRetry = 10; // adapt according to used VIm
    while (output.contains("heartbeat") || output.contains("Vim Added") && retry < maxRetry)
      synchronized (mon) {
        mon.wait(1000);
        retry++;
      }

    System.out.println("ServiceDeployResponse: ");
    System.out.println(output);
    Assert.assertTrue("No Deploy service response received", retry < maxRetry);
    ServiceDeployResponse response = mapper.readValue(output, ServiceDeployResponse.class);
    Assert.assertTrue(response.getRequestStatus().equals("DEPLOYED"));
    Assert.assertTrue(response.getNsr().getStatus() == Status.offline);
    */

    //for (VnfRecord vnfr : response.getVnfrs())
    //  Assert.assertTrue(vnfr.getStatus() == Status.offline);


    // Service removal
    /*
    output = null;
    String instanceUuid = baseInstanceUuid + "-01";
    String message = "{\"instance_uuid\":\"" + instanceUuid + "\"}";
    topic = "infrastructure.service.remove";
    ServicePlatformMessage removeInstanceMessage = new ServicePlatformMessage(message,
        "application/json", topic, UUID.randomUUID().toString(), topic);
    consumer.injectMessage(removeInstanceMessage);

    while (output == null) {
      synchronized (mon) {
        mon.wait(2000);
        System.out.println(output);
      }
    }
    System.out.println(output);
    tokener = new JSONTokener(output);
    jsonObject = (JSONObject) tokener.nextValue();
    status = jsonObject.getString("request_status");
    Assert.assertTrue("Adapter returned an unexpected status: " + status, status.equals("SUCCESS"));
    */

    // VIM removal
    output = null;
    String message = "{\"uuid\":\"" + computeWrUuid + "\"}";
    topic = "infrastructure.management.compute.remove";
    ServicePlatformMessage removeVimMessage = new ServicePlatformMessage(message,
        "application/json", topic, UUID.randomUUID().toString(), topic);
    consumer.injectMessage(removeVimMessage);

    while (output == null) {
      synchronized (mon) {
        mon.wait(1000);
      }
    }
    System.out.println(output);
    tokener = new JSONTokener(output);
    jsonObject = (JSONObject) tokener.nextValue();
    status = jsonObject.getString("status");
    Assert.assertTrue(status.equals("COMPLETED"));

    /*
    // remove NetVim
    output = null;
    message = "{\"uuid\":\"" + netWrUuid + "\"}";
    topic = "infrastructure.management.compute.remove";
    ServicePlatformMessage removeNetVimMessage = new ServicePlatformMessage(message,
        "application/json", topic, UUID.randomUUID().toString(), topic);
    consumer.injectMessage(removeNetVimMessage);

    while (output == null) {
      synchronized (mon) {
        mon.wait(1000);
      }
    }
    System.out.println(output);
    tokener = new JSONTokener(output);
    jsonObject = (JSONObject) tokener.nextValue();
    status = jsonObject.getString("status");
    Assert.assertTrue(status.equals("COMPLETED"));

    core.stop();


    // clean the SFC engine
    /*
    System.out.println("Cleaning the SFC environment...");
    WrapperConfiguration config = new WrapperConfiguration();
    config.setVimEndpoint("10.100.32.200");
    OvsWrapper wrapper = new OvsWrapper(config);
    wrapper.deconfigureNetworking(dataV1.getNsd().getInstanceUuid());
    */
  }

  @Test
  public void testPrepareService() throws JsonProcessingException {

    ServicePreparePayload payload = new ServicePreparePayload();

    payload.setInstanceId(data.getNsd().getInstanceUuid());
    ArrayList<VimPreDeploymentList> vims = new ArrayList<VimPreDeploymentList>();
    VimPreDeploymentList vimDepList = new VimPreDeploymentList();
    vimDepList.setUuid("aaaa-aaaaaaaaaaaaa-aaaaaaaaaaaaa-aaaaaaaa");
    ArrayList<VnfImage> vnfImages = new ArrayList<VnfImage>();
    VnfImage Image1 = new VnfImage("eu.sonata-nfv:1-vnf:0.1:1", "file:///test_images/sonata-1");
    VnfImage Image2 = new VnfImage("eu.sonata-nfv:2-vnf:0.1:1", "file:///test_images/sonata-2");
    VnfImage Image3 = new VnfImage("eu.sonata-nfv:3-vnf:0.1:1", "file:///test_images/sonata-3");
    VnfImage Image4 = new VnfImage("eu.sonata-nfv:4-vnf:0.1:1", "file:///test_images/sonata-4");
    vnfImages.add(Image1);
    vnfImages.add(Image2);
    vnfImages.add(Image3);
    vnfImages.add(Image4);
    vimDepList.setImages(vnfImages);
    vims.add(vimDepList);


    vimDepList = new VimPreDeploymentList();
    vimDepList.setUuid("bbbb-bbbbbbbbbbbb-bbbbbbbbbbbb-bbbbbbbbb");
    vnfImages = new ArrayList<VnfImage>();
    VnfImage Image5 = new VnfImage("eu.sonata-nfv:5-vnf:0.1:1", "file:///test_images/sonata-5");
    VnfImage Image6 = new VnfImage("eu.sonata-nfv:6-vnf:0.1:1", "file:///test_images/sonata-6");
    VnfImage Image7 = new VnfImage("eu.sonata-nfv:7-vnf:0.1:1", "file:///test_images/sonata-7");
    vnfImages.add(Image5);
    vnfImages.add(Image6);
    vnfImages.add(Image7);
    vimDepList.setImages(vnfImages);
    vims.add(vimDepList);

    payload.setVimList(vims);

    // System.out.println(mapper.writeValueAsString(payload));
  }

  public void receiveHeartbeat(ServicePlatformMessage message) {
    synchronized (mon) {
      this.lastHeartbeat = message.getBody();
      mon.notifyAll();
    }
  }

  public void receive(ServicePlatformMessage message) {
    synchronized (mon) {
      this.output = message.getBody();
      mon.notifyAll();
    }
  }

  public void forwardToConsumer(ServicePlatformMessage message) {
    consumer.injectMessage(message);
  }
}
