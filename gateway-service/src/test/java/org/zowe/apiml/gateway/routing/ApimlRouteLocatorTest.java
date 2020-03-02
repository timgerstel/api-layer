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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
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

public class ApimlRouteLocatorTest {

    private ApimlRouteLocator apimlRouteLocator;
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
        apimlRouteLocator = new ApimlRouteLocator("service", discovery, properties, serviceRouteMapper, routedServicesUsers);
    }

    @Test
    public void givenPopulatedDiscovery_whenEverythingValid_thenLocateRoutesCorrectly() {

        DiscoveryMock dmock = new DiscoveryMock();
        dmock.addServiceInstance("service");
        dmock.addRouteToInstance("service0:localhost:80", "api-v1", "api/v1", "/");
        dmock.build();

        LinkedHashMap<String, ZuulProperties.ZuulRoute> locatedRoutes = apimlRouteLocator.locateRoutes();

        assertThat(locatedRoutes, hasEntry("/api/v1/service/**", createZuulRoute("api/v1/service", "/api/v1/service/**", "service")));
    }

    @Test
    public void givenSingleServiceWithTwoRoutes_whenEverythingValid_thenFindsTwoExpectedRoutesEntries() {

        DiscoveryMock dmock = new DiscoveryMock();
        dmock.addServiceInstance("service");
        dmock.addRouteToInstance("service0:localhost:80", "api-v1", "api/v1", "/");
        dmock.addRouteToInstance("service0:localhost:80", "ws-v1", "ws/v1", "/ws");
        dmock.build();

        LinkedHashMap<String, ZuulProperties.ZuulRoute> locatedRoutes = apimlRouteLocator.locateRoutes();

        assertThat(locatedRoutes, hasEntry("/api/v1/service/**", createZuulRoute("api/v1/service", "/api/v1/service/**", "service")));
        assertThat(locatedRoutes, hasEntry("/ws/v1/service/**", createZuulRoute("ws/v1/service", "/ws/v1/service/**", "service")));
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
            ServiceInstance instance = new DefaultServiceInstance( serviceId+instanceCount+":localhost:80", serviceId,
                "localhost", 80, false, Collections.EMPTY_MAP);
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

            ServiceInstance newInstance = new DefaultServiceInstance(instance.getInstanceId(), instance.getServiceId(), instance.getHost(), instance.getPort(), instance.isSecure(), metadata);
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
        DiscoveryMock dmock = new DiscoveryMock();
        dmock.addServiceInstance("myService");
        dmock.build();

        assertThat(discovery.getServices(), hasSize(1));
        assertThat(discovery.getServices(), contains("myService"));

        assertThat(discovery.getInstances("myService"), hasSize(1));
    }

    @Test
    public void givenMockDiscovery_whenAddMultipleServices_thenMockReturnsInstancesCorrectlyNumbered() {
        DiscoveryMock dmock = new DiscoveryMock();
        dmock.addServiceInstance("myService");
        dmock.addServiceInstance("myService");
        dmock.build();

        assertThat(discovery.getServices(), hasSize(1));
        assertThat(discovery.getServices(), contains("myService"));

        assertThat(discovery.getInstances("myService"), hasSize(2));

    }

    @Test
    public void givenMockDiscovery_whenRouteAddedToService_thenInstanceReceivesCorrectMetadata() {
        DiscoveryMock dmock = new DiscoveryMock();
        dmock.addServiceInstance("myService");
        dmock.addRouteToInstance("myService0:localhost:80", "ws-v1", "ws/v1", "/servicepath");
        dmock.addRouteToInstance("myService0:localhost:80", "api-v1","api/v1", "/api");
        dmock.build();

        assertThat(discovery.getServices(), contains("myService"));
        assertThat(discovery.getInstances("myService"), hasSize(1));
        ServiceInstance instance = discovery.getInstances("myService").get(0);
        assertThat(instance.getMetadata(), hasEntry(ROUTES + ".ws-v1." + ROUTES_GATEWAY_URL, "ws/v1"));
        assertThat(instance.getMetadata(), hasEntry(ROUTES + ".ws-v1." + ROUTES_SERVICE_URL, "/servicepath"));
        assertThat(instance.getMetadata(), hasEntry(ROUTES + ".api-v1." + ROUTES_GATEWAY_URL, "api/v1"));
        assertThat(instance.getMetadata(), hasEntry(ROUTES + ".api-v1." + ROUTES_SERVICE_URL, "/api"));
    }




    /*
    @Test
    public void shouldLocateRoutes() {
        apimlRouteLocator = new ApimlRouteLocator("service", discovery, properties, serviceRouteMapper, routedServicesUsers);

        Map<String, String> metadata = new HashMap<>();
        metadata.put(ROUTES + ".api-v1." + ROUTES_GATEWAY_URL, "api/v1");
        metadata.put(ROUTES + ".api-v1." + ROUTES_SERVICE_URL, "/");
        when(discovery.getServices()).thenReturn(Collections.singletonList("service"));
        when(discovery.getInstances("service")).thenReturn(
            Collections.singletonList(new DefaultServiceInstance("service", "localhost", 80, false, metadata)));
        when(serviceRouteMapper.apply("service")).thenReturn("service");

        LinkedHashMap<String, ZuulProperties.ZuulRoute> routes = apimlRouteLocator.locateRoutes();

        ZuulProperties.ZuulRoute expectZuulRoute = new ZuulProperties.ZuulRoute();
        expectZuulRoute.setId("api/v1/service");
        expectZuulRoute.setPath("/api/v1/service/**");
        expectZuulRoute.setServiceId("service");
        LinkedHashMap<String, ZuulProperties.ZuulRoute> expectedRoutes = new LinkedHashMap();
        expectedRoutes.put("/api/v1/service/**", expectZuulRoute);
        assertEquals(expectedRoutes, routes);
    }
    @Test
    public void shouldReturnNull_WhenLocateRoutes_IfServiceInstanceNull() {
        apimlRouteLocator = new ApimlRouteLocator("service", discovery, properties, serviceRouteMapper, routedServicesUsers);

        Map<String, String> metadata = new HashMap<>();
        metadata.put(ROUTES + ".api-v1." + ROUTES_GATEWAY_URL, "api/v1");
        metadata.put(ROUTES + ".api-v1." + ROUTES_SERVICE_URL, "/");
        when(discovery.getServices()).thenReturn(Collections.singletonList("service"));
        when(discovery.getInstances("service")).thenReturn(null);
        LinkedHashMap<String, ZuulProperties.ZuulRoute> routes = apimlRouteLocator.locateRoutes();
        assertEquals(null, routes);
    }
    @Test
    public void shouldPopulateRoutesMap_WhenLocateRoutes_IfServiceIdDifferentFromServletPath() {
        apimlRouteLocator = new ApimlRouteLocator("service", discovery, properties, serviceRouteMapper, routedServicesUsers);

        Map<String, String> metadata = new HashMap<>();
        metadata.put(ROUTES + ".api-v1." + ROUTES_GATEWAY_URL, "api/v1");
        metadata.put(ROUTES + ".api-v1." + ROUTES_SERVICE_URL, "/");
        when(discovery.getServices()).thenReturn(Collections.singletonList("differentId"));
        when(discovery.getInstances("differentId")).thenReturn(
            Collections.singletonList(new DefaultServiceInstance("differentId", "localhost", 80, false, metadata)));
        when(serviceRouteMapper.apply("differentId")).thenReturn("differentId");
        LinkedHashMap<String, ZuulProperties.ZuulRoute> routes = apimlRouteLocator.locateRoutes();
        ZuulProperties.ZuulRoute expectZuulRoute = new ZuulProperties.ZuulRoute();
        expectZuulRoute.setId("api/v1/differentId");
        expectZuulRoute.setPath("/api/v1/differentId/**");
        expectZuulRoute.setServiceId("differentId");
        LinkedHashMap<String, ZuulProperties.ZuulRoute> expectedRoutes = new LinkedHashMap();
        expectedRoutes.put("/api/v1/differentId/**", expectZuulRoute);
        assertEquals(expectedRoutes, routes);
    }
    @Test
    public void shouldConcatenatePrefix_WhenLocateRoutes_IfPrefixIsPresent() {
        apimlRouteLocator = new ApimlRouteLocator("service", discovery, properties, serviceRouteMapper, routedServicesUsers);

        Map<String, String> metadata = new HashMap<>();
        metadata.put(ROUTES + ".api-v1." + ROUTES_GATEWAY_URL, "api/v1");
        metadata.put(ROUTES + ".api-v1." + ROUTES_SERVICE_URL, "api");
        when(discovery.getServices()).thenReturn(Collections.singletonList("service"));
        when(discovery.getInstances("service")).thenReturn(
            Collections.singletonList(new DefaultServiceInstance("service", "localhost", 80, false, metadata)));
        when(serviceRouteMapper.apply("service")).thenReturn("service");
        when(properties.getPrefix()).thenReturn("prefix");
        LinkedHashMap<String, ZuulProperties.ZuulRoute> routes = apimlRouteLocator.locateRoutes();
        ZuulProperties.ZuulRoute expectZuulRoute = new ZuulProperties.ZuulRoute();
        expectZuulRoute.setId("service");
        expectZuulRoute.setPath("/service/**");
        expectZuulRoute.setServiceId("service");
        LinkedHashMap<String, ZuulProperties.ZuulRoute> expectedRoutes = new LinkedHashMap();
        expectedRoutes.put("/prefix/prefix/service/**", expectZuulRoute);
        ZuulProperties.ZuulRoute expectZuulRoute2 = new ZuulProperties.ZuulRoute();
        expectZuulRoute2.setId("api/v1/service");
        expectZuulRoute2.setPath("/api/v1/service/**");
        expectZuulRoute2.setServiceId("service");
        LinkedHashMap<String, ZuulProperties.ZuulRoute> expectedRoutes2 = new LinkedHashMap();
        expectedRoutes2.put("/prefix/api/v1/service/**", expectZuulRoute2);
        assertEquals(expectedRoutes.get("/prefix/prefix/service/**"), routes.get("/prefix/prefix/service/**"));
        assertEquals(expectedRoutes2.get("/prefix/api/v1/service/**"), routes.get("/prefix/api/v1/service/**"));
    }
    @Test
    public void shouldAddRoutedService_WhenLocateRoutes_IfRoutedServiceExists() {
        routedServicesUsers.add(locationFilter);
        apimlRouteLocator = new ApimlRouteLocator("service", discovery, properties, serviceRouteMapper, routedServicesUsers);
        Map<String, String> metadata = new HashMap<>();
        metadata.put(ROUTES + ".api-v1." + ROUTES_GATEWAY_URL, "api/v1");
        metadata.put(ROUTES + ".api-v1." + ROUTES_SERVICE_URL, "api");
        when(discovery.getServices()).thenReturn(Collections.singletonList("service"));
        when(discovery.getInstances("service")).thenReturn(
            Collections.singletonList(new DefaultServiceInstance("service", "localhost", 80, false, metadata)));
        when(serviceRouteMapper.apply("service")).thenReturn("service");
        LinkedHashMap<String, ZuulProperties.ZuulRoute> routes = apimlRouteLocator.locateRoutes();
        ZuulProperties.ZuulRoute expectZuulRoute = new ZuulProperties.ZuulRoute();
        expectZuulRoute.setId("api/v1/service");
        expectZuulRoute.setPath("/api/v1/service/**");
        expectZuulRoute.setServiceId("service");
        LinkedHashMap<String, ZuulProperties.ZuulRoute> expectedRoutes = new LinkedHashMap();
        expectedRoutes.put("/api/v1/service/**", expectZuulRoute);
        assertEquals(expectedRoutes, routes);
    }

     */
}
