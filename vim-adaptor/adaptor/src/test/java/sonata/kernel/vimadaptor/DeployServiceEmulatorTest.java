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
  private VnfDescriptor vnfd_apache;
  private VnfDescriptor vnfd_socat;
  private VnfDescriptor vnfd_squid;
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
        new FileInputStream(new File("./YAML/emulator-demo-nsd.yml")), Charset.forName("UTF-8")));
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

    bodyBuilder = new StringBuilder();
    in = new BufferedReader(new InputStreamReader(
        new FileInputStream(new File("./YAML/emulator-demo-l4fw-socat-vnfd.yml")), Charset.forName("UTF-8")));
    line = null;
    while ((line = in.readLine()) != null)
      bodyBuilder.append(line + "\n\r");
    this.vnfd_socat = mapper.readValue(bodyBuilder.toString(), VnfDescriptor.class);

    bodyBuilder = new StringBuilder();
    in = new BufferedReader(new InputStreamReader(
        new FileInputStream(new File("./YAML/emulator-demo-proxy-squid-vnfd.yml")), Charset.forName("UTF-8")));
    line = null;
    while ((line = in.readLine()) != null)
      bodyBuilder.append(line + "\n\r");
    this.vnfd_squid = mapper.readValue(bodyBuilder.toString(), VnfDescriptor.class);

    bodyBuilder = new StringBuilder();
    in = new BufferedReader(new InputStreamReader(
            new FileInputStream(new File("./YAML/emulator-demo-http-apache-vnfd.yml")), Charset.forName("UTF-8")));
    line = null;
    while ((line = in.readLine()) != null)
      bodyBuilder.append(line + "\n\r");
    this.vnfd_apache = mapper.readValue(bodyBuilder.toString(), VnfDescriptor.class);

    this.data = new ServiceDeployPayload();
    this.data.setServiceDescriptor(sd);
    this.data.addVnfDescriptor(this.vnfd_apache);
    this.data.addVnfDescriptor(this.vnfd_socat);
    this.data.addVnfDescriptor(this.vnfd_squid);
  }

  /**
   * This test is de-activated, if you want to use it with your NFVi-PoP, please edit the addVimBody
   * and addNetVimBody String Member to match your OpenStack and ovs configuration and substitute
   * the @Ignore annotation with the @Test annotation
   *
   * @throws Exception
   */
  //@Test //@Ignore
  @Test
  public void testDeployServiceEmulatorOpenStackApi() throws Exception {

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


    // Add first PoP
    System.out.println("[EmulatorTest] Adding PoP 1");
    String addVimBody = "{\"vim_type\":\"Heat\", " + "\"configuration\":{"
            + "\"tenant_ext_router\":\"26f732b2-74bd-4f8c-a60e-dae4fb6a7c14\", "
            + "\"tenant_ext_net\":\"53d43a3e-8c86-48e6-b1cb-f1f2c48833de\"," + "\"tenant\":\"tenantName\","
            + "\"identity_port\":\"6001\""
            + "}," + "\"city\":\"Paderborn\",\"country\":\"Germany\","
            + "\"vim_address\":\"172.0.0.101\",\"username\":\"username\","
            + "\"name\":\"EmulatorVim1\","
            + "\"pass\":\"password\"}";

    String topic = "infrastructure.management.compute.add";
    ServicePlatformMessage addVimMessage = new ServicePlatformMessage(addVimBody, "application/json", topic,
            UUID.randomUUID().toString(), topic);
    consumer.injectMessage(addVimMessage);
    Thread.sleep(2000);
    while (output == null)
      synchronized (mon) {
        mon.wait(1000);
      }

    JSONTokener tokener = new JSONTokener(output);
    JSONObject jsonObject = (JSONObject) tokener.nextValue();
    String status = jsonObject.getString("request_status");
    String computeWrUuid1 = jsonObject.getString("uuid");
    Assert.assertTrue(status.equals("COMPLETED"));
    System.out.println("OpenStack Wrapper added, with uuid: " + computeWrUuid1);

    /* we are not allowed to specify ports so a second emulator PoP does not work for nows
    // Add second PoP
    System.out.println("[EmulatorTest] Adding PoP 2");
    addVimBody = "{\"vim_type\":\"Heat\", " + "\"configuration\":{"
            + "\"tenant_ext_router\":\"26f732b2-74bd-4f8c-a60e-dae4fb6a7c14\", "
            + "\"tenant_ext_net\":\"53d43a3e-8c86-48e6-b1cb-f1f2c48833de\"," + "\"tenant\":\"tenantName\""
            + "}," + "\"city\":\"Paderborn\",\"country\":\"Germany\","
            + "\"vim_address\":\"127.0.0.1\",\"username\":\"username\","
            +"\"name\":\"EmulatorVim2\","
            + "\"pass\":\"password\"}";

    topic = "infrastructure.management.compute.add";
    addVimMessage = new ServicePlatformMessage(addVimBody, "application/json", topic,
            UUID.randomUUID().toString(), topic);
    consumer.injectMessage(addVimMessage);
    Thread.sleep(2000);
    while (output == null)
      synchronized (mon) {
        mon.wait(1000);
      }

    tokener = new JSONTokener(output);
    jsonObject = (JSONObject) tokener.nextValue();
    status = jsonObject.getString("request_status");
    String computeWrUuid = jsonObject.getString("uuid");
    Assert.assertTrue(status.equals("COMPLETED"));
    System.out.println("OpenStack Wrapper added, with uuid: " + computeWrUuid);
    */

    /*
    output = null;
    addNetVimBody = "{\"vim_type\":\"ovs\", "
            +"\"name\":\"Athens2-net\","+ "\"vim_address\":\"10.100.32.10\",\"username\":\"operator\",\"city\":\"Athens\",\"country\":\"Greece\","
            + "\"pass\":\"apass\",\"configuration\":{\"compute_uuid\":\"" + computeWrUuid2 + "\"}}";
    topic = "infrastructure.management.network.add";
    addNetVimMessage = new ServicePlatformMessage(addNetVimBody, "application/json", topic,
            UUID.randomUUID().toString(), topic);
    consumer.injectMessage(addNetVimMessage);
    Thread.sleep(2000);
    while (output == null)
      synchronized (mon) {
        mon.wait(1000);
      }

    tokener = new JSONTokener(output);
    jsonObject = (JSONObject) tokener.nextValue();
    status = null;
    status = jsonObject.getString("request_status");
    String netWrUuid2 = jsonObject.getString("uuid");
    Assert.assertTrue("Failed to add the ovs wrapper. Status " + status,
            status.equals("COMPLETED"));
    System.out.println("OVS Wrapper added, with uuid: " + netWrUuid2);
    */

    // List available PoP
    output = null;
    System.out.println("[EmulatorTest] Listing available NFVIi-PoP.");

    topic = "infrastructure.management.compute.list";
    ServicePlatformMessage listVimMessage =
            new ServicePlatformMessage(null, null, topic, UUID.randomUUID().toString(), topic);
    consumer.injectMessage(listVimMessage);

    while (output == null) {
      synchronized (mon) {
        mon.wait(1000);
      }
    }
    System.out.println(output);
    VimResources[] vimList = mapper.readValue(output, VimResources[].class);
    System.out.println("[TwoPoPTest] Listing available PoP");
    for (VimResources resource : vimList) {
      System.out.println(mapper.writeValueAsString(resource));
    }

    output = null;
    // Prepare the system for a service deployment
    System.out.println("[EmulatorTest] Building service.prepare call.");

    ServicePreparePayload payload = new ServicePreparePayload();

    payload.setInstanceId(data.getNsd().getInstanceUuid());
    ArrayList<VimPreDeploymentList> vims = new ArrayList<VimPreDeploymentList>();
    VimPreDeploymentList vimDepList = new VimPreDeploymentList();
    vimDepList.setUuid(computeWrUuid1);
    ArrayList<VnfImage> vnfImages = new ArrayList<VnfImage>();
    VnfImage vnfImgae1 =
            new VnfImage("sonata-squid",
                            "docker://sonata-squid:latest");
    VnfImage vnfImgae2 =
            new VnfImage("sonata-socat",
                    "docker://sonata-socat:latest");
    VnfImage vnfImgae3 =
            new VnfImage("sonata-apache",
                    "docker://sonata-apache:latest");

    vnfImages.add(vnfImgae1);
    vnfImages.add(vnfImgae2);
    vnfImages.add(vnfImgae3);

    vimDepList.setImages(vnfImages);
    vims.add(vimDepList);

    /* second PoP preparation
    vimDepList = new VimPreDeploymentList();
    vimDepList.setUuid(computeWrUuid2);
    vnfImages = new ArrayList<VnfImage>();
    VnfImage vfwImgade =
            // new VnfImage("eu.sonata-nfv_fw-vnf_0.1_1", "file:///test_images/sonata-vfw.img");
            new VnfImage("eu.sonata-nfv_fw-vnf_0.1_1",
                    "http://download.cirros-cloud.net/0.3.5/cirros-0.3.5-x86_64-disk.img");
    vnfImages.add(vfwImgade);
    vimDepList.setImages(vnfImages);
    vims.add(vimDepList);
    */

    payload.setVimList(vims);

    String body = mapper.writeValueAsString(payload);
    System.out.println("[EmulatorTest] Request body:");
    System.out.println(body);

    topic = "infrastructure.service.prepare";
    ServicePlatformMessage servicePrepareMessage = new ServicePlatformMessage(body,
            "application/x-yaml", topic, UUID.randomUUID().toString(), topic);

    consumer.injectMessage(servicePrepareMessage);

    Thread.sleep(2000);
    while (output == null)
      synchronized (mon) {
        mon.wait(1000);
      }

    tokener = new JSONTokener(output);
    jsonObject = (JSONObject) tokener.nextValue();
    status = null;
    status = jsonObject.getString("request_status");
    String message = jsonObject.getString("message");
    Assert.assertTrue("Failed to prepare the environment for the service deployment: " + status
            + " - message: " + message, status.equals("COMPLETED"));
    System.out.println("Service " + payload.getInstanceId() + " ready for deployment");

    // Deploy each of the VNFs
    ArrayList<VnfRecord> records = new ArrayList<VnfRecord>();

    // deploy apache
    output = null;
    FunctionDeployPayload vnfPayload = new FunctionDeployPayload();
    vnfPayload.setVnfd(this.vnfd_apache);
    vnfPayload.setVimUuid(computeWrUuid1);
    vnfPayload.setServiceInstanceId(data.getNsd().getInstanceUuid());
    body = mapper.writeValueAsString(vnfPayload);
    topic = "infrastructure.function.deploy";
    ServicePlatformMessage functionDeployMessage = new ServicePlatformMessage(body,
            "application/x-yaml", topic, UUID.randomUUID().toString(), topic);
    consumer.injectMessage(functionDeployMessage);
    Thread.sleep(2000);
    while (output == null)
      synchronized (mon) {
        mon.wait(1000);
      }
    Assert.assertNotNull(output);
    int retry = 0;
    int maxRetry = 60;
    while (output.contains("heartbeat") || output.contains("Vim Added") && retry < maxRetry) {
      synchronized (mon) {
        mon.wait(1000);
        retry++;
      }
    }

    System.out.println("FunctionDeployResponse: ");
    System.out.println(output);
    Assert.assertTrue("No response received after function deployment", retry < maxRetry);
    FunctionDeployResponse response = mapper.readValue(output, FunctionDeployResponse.class);
    Assert.assertTrue(response.getRequestStatus().equals("COMPLETED"));
    Assert.assertTrue(response.getVnfr().getStatus() == Status.offline);
    records.add(response.getVnfr());

    // deploy socat
    output = null;
    vnfPayload = new FunctionDeployPayload();
    vnfPayload.setVnfd(this.vnfd_socat);
    vnfPayload.setVimUuid(computeWrUuid1);
    vnfPayload.setServiceInstanceId(data.getNsd().getInstanceUuid());
    body = mapper.writeValueAsString(vnfPayload);

    topic = "infrastructure.function.deploy";
    functionDeployMessage = new ServicePlatformMessage(body,
            "application/x-yaml", topic, UUID.randomUUID().toString(), topic);
    consumer.injectMessage(functionDeployMessage);
    Thread.sleep(2000);
    while (output == null)
      synchronized (mon) {
        mon.wait(1000);
      }
    Assert.assertNotNull(output);
    retry = 0;
    maxRetry = 60;
    while (output.contains("heartbeat") || output.contains("Vim Added") && retry < maxRetry) {
      synchronized (mon) {
        mon.wait(1000);
        retry++;
      }
    }
    System.out.println("FunctionDeployResponse: ");
    System.out.println(output);
    Assert.assertTrue("No response received after function deployment", retry < maxRetry);
    response = mapper.readValue(output, FunctionDeployResponse.class);
    Assert.assertTrue(response.getRequestStatus().equals("COMPLETED"));
    Assert.assertTrue(response.getVnfr().getStatus() == Status.offline);
    records.add(response.getVnfr());

    // deploy squid
    output = null;
    vnfPayload = new FunctionDeployPayload();
    vnfPayload.setVnfd(this.vnfd_squid);
    vnfPayload.setVimUuid(computeWrUuid1);
    vnfPayload.setServiceInstanceId(data.getNsd().getInstanceUuid());
    body = mapper.writeValueAsString(vnfPayload);

    topic = "infrastructure.function.deploy";
    functionDeployMessage = new ServicePlatformMessage(body,
            "application/x-yaml", topic, UUID.randomUUID().toString(), topic);
    consumer.injectMessage(functionDeployMessage);
    Thread.sleep(2000);
    while (output == null)
      synchronized (mon) {
        mon.wait(1000);
      }
    Assert.assertNotNull(output);
    retry = 0;
    maxRetry = 60;
    while (output.contains("heartbeat") || output.contains("Vim Added") && retry < maxRetry) {
      synchronized (mon) {
        mon.wait(1000);
        retry++;
      }
    }
    System.out.println("FunctionDeployResponse: ");
    System.out.println(output);
    Assert.assertTrue("No response received after function deployment", retry < maxRetry);
    response = mapper.readValue(output, FunctionDeployResponse.class);
    Assert.assertTrue(response.getRequestStatus().equals("COMPLETED"));
    Assert.assertTrue(response.getVnfr().getStatus() == Status.offline);
    records.add(response.getVnfr());



    /*

    // vFw VNF in PoP#2
    output = null;
    response = null;

    vnfPayload = new FunctionDeployPayload();
    vnfPayload.setVnfd(vfwVnfd);
    vnfPayload.setVimUuid(computeWrUuid2);
    vnfPayload.setServiceInstanceId(data.getNsd().getInstanceUuid());
    body = mapper.writeValueAsString(vnfPayload);

    topic = "infrastructure.function.deploy";
    functionDeployMessage = new ServicePlatformMessage(body, "application/x-yaml", topic,
            UUID.randomUUID().toString(), topic);

    consumer.injectMessage(functionDeployMessage);

    Thread.sleep(2000);
    while (output == null)
      synchronized (mon) {
        mon.wait(1000);
      }
    Assert.assertNotNull(output);
    retry = 0;
    maxRetry = 60;
    while (output.contains("heartbeat") || output.contains("Vim Added") && retry < maxRetry) {
      synchronized (mon) {
        mon.wait(1000);
        retry++;
      }
    }

    System.out.println("FunctionDeployResponse: ");
    System.out.println(output);
    Assert.assertTrue("No response received after function deployment", retry < maxRetry);
    response = mapper.readValue(output, FunctionDeployResponse.class);
    Assert.assertTrue(response.getRequestStatus().equals("COMPLETED"));
    Assert.assertTrue(response.getVnfr().getStatus() == Status.offline);
    records.add(response.getVnfr());

    */

    /*

    // Finally configure Networking in each NFVi-PoP (VIMs)

    output = null;

    NetworkConfigurePayload netPayload = new NetworkConfigurePayload();
    netPayload.setNsd(data.getNsd());
    netPayload.setVnfds(data.getVnfdList());
    netPayload.setVnfrs(records);
    netPayload.setServiceInstanceId(data.getNsd().getInstanceUuid());


    body = mapper.writeValueAsString(netPayload);

    topic = "infrastructure.service.chain.configure";
    ServicePlatformMessage networkConfigureMessage = new ServicePlatformMessage(body,
            "application/x-yaml", topic, UUID.randomUUID().toString(), topic);

    consumer.injectMessage(networkConfigureMessage);

    Thread.sleep(2000);
    while (output == null)
      synchronized (mon) {
        mon.wait(1000);
      }

    System.out.println(output);
    tokener = new JSONTokener(output);
    jsonObject = (JSONObject) tokener.nextValue();
    status = null;
    status = jsonObject.getString("request_status");
    Assert.assertTrue("Failed to configure inter-PoP SFC. status:" + status,
            status.equals("COMPLETED"));
    System.out.println(
            "Service " + payload.getInstanceId() + " deployed and configured in selected VIM(s)");

    output = null;

    */

    /*
    // deploy service (V1)
    output = null;
    String baseInstanceUuid = data.getNsd().getInstanceUuid();
    data.setVimUuid(computeWrUuid);
    data.getNsd().setInstanceUuid(""); // lets keep the name short! //(baseInstanceUuid + "-01");

    String body = mapper.writeValueAsString(data);

    topic = "infrastructure.service.deploy";
    ServicePlatformMessage deployServiceMessage = new ServicePlatformMessage(body,
        "application/x-yaml", topic, UUID.randomUUID().toString(), topic);

    consumer.injectMessage(deployServiceMessage);

    Thread.sleep(2000);
    while (output == null)
      synchronized (mon) {
        mon.wait(1000);
      }
    Assert.assertNotNull(output);
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
    //ServiceDeployResponse response = mapper.readValue(output, ServiceDeployResponse.class);
    //Assert.assertTrue(response.getRequestStatus().equals("DEPLOYED"));
    //Assert.assertTrue(response.getNsr().getStatus() == Status.offline);


    //for (VnfRecord vnfr : response.getVnfrs())
    //  Assert.assertTrue(vnfr.getStatus() == Status.offline);


   */
  }

  @Ignore
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
