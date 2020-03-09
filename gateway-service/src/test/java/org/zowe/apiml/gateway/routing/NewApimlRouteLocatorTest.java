/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */

package org.zowe.apiml.gateway.routing;

import com.netflix.appinfo.InstanceInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.netflix.eureka.EurekaDiscoveryClient;
import org.springframework.cloud.netflix.zuul.filters.ZuulProperties;
import org.springframework.cloud.netflix.zuul.filters.discovery.ServiceRouteMapper;
import org.zowe.apiml.gateway.filters.pre.LocationFilter;
import org.zowe.apiml.product.routing.RoutedServicesUser;

import java.util.*;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.zowe.apiml.constants.EurekaMetadataDefinition.*;

class NewApimlRouteLocatorTest {

    private NewApimlRouteLocator newApimlRouteLocator;
    private DiscoveryClient discovery;
    private ZuulProperties properties;
    private List<RoutedServicesUser> routedServicesUsers;
    private ServiceRouteMapper serviceRouteMapper;
    private LocationFilter locationFilter;

    @BeforeEach
    public void setup() {
        discovery = mock(DiscoveryClient.class);
        properties = mock(ZuulProperties.class);
        serviceRouteMapper = mock(ServiceRouteMapper.class);
        locationFilter = mock(LocationFilter.class);
        routedServicesUsers = new ArrayList<>();
        when(serviceRouteMapper.apply(anyString())).thenAnswer(i -> i.getArgument(0));
        newApimlRouteLocator = new NewApimlRouteLocator("service", discovery, properties, serviceRouteMapper, routedServicesUsers);
    }

    @Test
    void givenService_whenDoesNotHaveRouteMetadata_thenCreateNoRoute() {
        NewApimlRouteLocatorTest.DiscoveryMock dmock = new NewApimlRouteLocatorTest.DiscoveryMock();
        dmock.addServiceInstance("service");
        dmock.build();

        LinkedHashMap<String, ZuulProperties.ZuulRoute> locatedRoutes = newApimlRouteLocator.locateRoutes();

        assertThat(locatedRoutes.entrySet(), hasSize(0));
    }

    @Test
    void givenStaticService_whenLocate_thenDefaultRouteRemoved() {
        NewApimlRouteLocatorTest.DiscoveryMock dmock = new NewApimlRouteLocatorTest.DiscoveryMock();
        dmock.addServiceInstanceWithNullUrl("service");
        dmock.build();

        LinkedHashMap<String, ZuulProperties.ZuulRoute> locatedRoutes = newApimlRouteLocator.locateRoutes();

        assertThat(locatedRoutes.entrySet(), hasSize(0));
    }





    /**
     * SERVICE MOCK HELPER CLASS AND IT'S TESTS
     */

    private final class DiscoveryMock{

        private List<ServiceInstance> serviceRegistry = new ArrayList<>();

        public ServiceInstance addServiceInstance(String serviceId) {
            long instanceCount = serviceRegistry.stream()
                .filter(i -> i.getServiceId().equals(serviceId))
                .count();
            InstanceInfo info = InstanceInfo.Builder.newBuilder()
                .setInstanceId(serviceId+instanceCount+":localhost:80")
                .setAppName(serviceId)
                .setVIPAddress("localhost")
                .setPort(80)
                .build();
            ServiceInstance instance = new EurekaDiscoveryClient.EurekaServiceInstance(info);
            serviceRegistry.add(instance);
            return instance;
        }

        public ServiceInstance addServiceInstanceWithNullUrl(String serviceId) {
            long instanceCount = serviceRegistry.stream()
                .filter(i -> i.getServiceId().equals(serviceId))
                .count();
            InstanceInfo info = InstanceInfo.Builder.newBuilder()
                .setInstanceId(serviceId+instanceCount+":localhost:80")
                .setAppName(serviceId)
                .build();
            ServiceInstance instance = new EurekaDiscoveryClient.EurekaServiceInstance(info);
            serviceRegistry.add(instance);
            return instance;
        }

        public ServiceInstance addRouteToInstance(String instanceId, String apiKey, String gatewayUrl, String serviceUrl) {
            ServiceInstance instance = serviceRegistry.stream()
                .filter(i -> i.getInstanceId().equals(instanceId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("instanceId not found in mock"));


            Map<String, String> metadata = new HashMap<>();
            metadata.putAll(instance.getMetadata());
            metadata.put(ROUTES + "."+apiKey+"." + ROUTES_GATEWAY_URL, gatewayUrl);
            metadata.put(ROUTES + "."+apiKey+"." + ROUTES_SERVICE_URL, serviceUrl);

            InstanceInfo info = ((EurekaDiscoveryClient.EurekaServiceInstance) instance).getInstanceInfo();

            InstanceInfo newInfo = InstanceInfo.Builder.newBuilder()
                .setInstanceId(info.getInstanceId())
                .setAppName(info.getAppName())
                .setVIPAddress(info.getVIPAddress())
                .setPort(info.getPort())
                .setMetadata(metadata)
                .build();
            ServiceInstance newInstance = new EurekaDiscoveryClient.EurekaServiceInstance(newInfo);
            serviceRegistry.remove(instance);
            serviceRegistry.add(newInstance);
            return newInstance;
        }

        public void build() {
            when(discovery.getServices()).thenReturn(serviceRegistry.stream()
                .map(i -> i.getServiceId())
                .distinct()
                .collect(Collectors.toList()));
            when(discovery.getInstances(anyString())).thenAnswer(
                call -> serviceRegistry.stream()
                    .filter(i -> i.getServiceId().equals(call.getArgument(0)))
                    .collect(Collectors.toList())
            );
        }

    }

    ZuulProperties.ZuulRoute createZuulRoute(String id, String path, String serviceId) {
        ZuulProperties.ZuulRoute route = new ZuulProperties.ZuulRoute();
        route.setId(id);
        route.setPath(path);
        route.setServiceId(serviceId);
        return route;
    }

    @Test
    public void givenMockDiscovery_whenAddService_thenMockSetsToReturnService() {
        NewApimlRouteLocatorTest.DiscoveryMock dmock = new NewApimlRouteLocatorTest.DiscoveryMock();
        dmock.addServiceInstance("myService");
        dmock.build();

        assertThat(discovery.getServices(), hasSize(1));
        assertThat(discovery.getServices(), contains("MYSERVICE"));

        assertThat(discovery.getInstances("MYSERVICE"), hasSize(1));
    }

    @Test
    public void givenMockDiscovery_whenAddMultipleServices_thenMockReturnsInstancesCorrectlyNumbered() {
        NewApimlRouteLocatorTest.DiscoveryMock dmock = new NewApimlRouteLocatorTest.DiscoveryMock();
        dmock.addServiceInstance("myService");
        dmock.addServiceInstance("myService");
        dmock.build();

        assertThat(discovery.getServices(), hasSize(1));
        assertThat(discovery.getServices(), contains("MYSERVICE"));

        assertThat(discovery.getInstances("MYSERVICE"), hasSize(2));

    }

    @Test
    public void givenMockDiscovery_whenRouteAddedToService_thenInstanceReceivesCorrectMetadata() {
        NewApimlRouteLocatorTest.DiscoveryMock dmock = new NewApimlRouteLocatorTest.DiscoveryMock();
        dmock.addServiceInstance("myService");
        dmock.addRouteToInstance("myService0:localhost:80", "ws-v1", "ws/v1", "/servicepath");
        dmock.addRouteToInstance("myService0:localhost:80", "api-v1","api/v1", "/api");
        dmock.build();

        assertThat(discovery.getServices(), contains("MYSERVICE"));
        assertThat(discovery.getInstances("MYSERVICE"), hasSize(1));
        ServiceInstance instance = discovery.getInstances("MYSERVICE").get(0);
        assertThat(instance.getMetadata(), hasEntry(ROUTES + ".ws-v1." + ROUTES_GATEWAY_URL, "ws/v1"));
        assertThat(instance.getMetadata(), hasEntry(ROUTES + ".ws-v1." + ROUTES_SERVICE_URL, "/servicepath"));
        assertThat(instance.getMetadata(), hasEntry(ROUTES + ".api-v1." + ROUTES_GATEWAY_URL, "api/v1"));
        assertThat(instance.getMetadata(), hasEntry(ROUTES + ".api-v1." + ROUTES_SERVICE_URL, "/api"));
    }

}
