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

package sonata.kernel.vimadaptor.commons.nsd;

import com.fasterxml.jackson.annotation.JsonProperty;

public class NetworkFunction {

  private String description;
  @JsonProperty("vnf_id")
  private String vnfId;
  @JsonProperty("vnf_name")
  private String vnfName;
  @JsonProperty("vnf_vendor")
  private String vnfVendor;
  @JsonProperty("vnf_version")
  private String vnfVersion;



  public String getDescription() {
    return description;
  }

  public String getVnfId() {
    return vnfId;
  }

  public String getVnfName() {
    return vnfName;
  }

  public String getVnfVendor() {
    return vnfVendor;
  }

  public String getVnfVersion() {
    return vnfVersion;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public void setVnfId(String vnfId) {
    this.vnfId = vnfId;
  }

  public void setVnfName(String vnfName) {
    this.vnfName = vnfName;
  }

  public void setVnfVendor(String vnfVendor) {
    this.vnfVendor = vnfVendor;
  }

  public void setVnfVersion(String vnfVersion) {
    this.vnfVersion = vnfVersion;
  }

}
