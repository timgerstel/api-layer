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

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.netflix.zuul.filters.ZuulProperties;
import org.springframework.cloud.netflix.zuul.filters.discovery.DiscoveryClientRouteLocator;
import org.springframework.cloud.netflix.zuul.filters.discovery.ServiceRouteMapper;
import org.springframework.util.PatternMatchUtils;
import org.springframework.util.StringUtils;
import org.zowe.apiml.eurekaservice.client.util.EurekaMetadataParser;
import org.zowe.apiml.message.log.ApimlLogger;
import org.zowe.apiml.product.logging.annotations.InjectApimlLogger;
import org.zowe.apiml.product.routing.RoutedServices;
import org.zowe.apiml.product.routing.RoutedServicesUser;

import java.util.*;

class ApimlRouteLocator extends DiscoveryClientRouteLocator {
    private final DiscoveryClient discovery;
    private final ZuulProperties properties;
    private final List<RoutedServicesUser> routedServicesUsers;
    private final EurekaMetadataParser eurekaMetadataParser;

    ApimlRouteLocator(String servletPath,
                      DiscoveryClient discovery,
                      ZuulProperties properties,
                      ServiceRouteMapper serviceRouteMapper,
                      List<RoutedServicesUser> routedServicesUsers) {
        super(servletPath, discovery, properties, serviceRouteMapper, null);
        this.discovery = discovery;
        this.properties = properties;
        this.routedServicesUsers = routedServicesUsers;
        this.eurekaMetadataParser = new EurekaMetadataParser();
    }

    @InjectApimlLogger
    private ApimlLogger apimlLog = ApimlLogger.empty();

    /**
     * Suppressing warnings instead of resolving them to match the original class
     * DiscoveryClientRouteLocator as much as possible
     */
    @Override
    @SuppressWarnings({"squid:MethodCyclomaticComplexity", "squid:S1075", "squid:S3776"})
    protected LinkedHashMap<String, ZuulProperties.ZuulRoute> locateRoutes() {

        LinkedHashMap<String, ZuulProperties.ZuulRoute> discoveryClientRouteLocatorRoutes = new LinkedHashMap<>(super.locateRoutes());

        LinkedHashMap<String, ZuulProperties.ZuulRoute> discoveredRoutes = new LinkedHashMap<>(discoveryClientRouteLocatorRoutes);

        if (this.discovery != null) {

            Map<String, ZuulProperties.ZuulRoute> staticServiceRoutes = extractStaticRoutes(discoveredRoutes);

            // Add routes for discovered services and itself by default
            List<String> servicesFromDiscovery = this.discovery.getServices();

            String[] ignoredServiceIds = this.properties.getIgnoredServices()
                .toArray(new String[0]);

            Set<String> removedRoutes = new HashSet<>();

            for (String serviceId : servicesFromDiscovery) {

                //TODO why return null if just one serviceInstances is null? There can be multiple sets of serviceInstances..
                List<ServiceInstance> serviceInstances = this.discovery.getInstances(serviceId);
                if (serviceInstances == null || serviceInstances.isEmpty()) {
                    continue;
                }

                //TODO this is registry of service and its routes? It is populated by createRouteKeys
                RoutedServices serviceRouteTable = new RoutedServices();
                List<String> serviceRouteKeys = createRouteKeys(serviceInstances, serviceRouteTable, serviceId);
                //TODO default route key?
                if (serviceRouteKeys.isEmpty()) {
                    serviceRouteKeys.add("/" + mapRouteToService(serviceId) + "/**");
                }

                //TODO fill route table to all it's users, isn't it early?
                for (RoutedServicesUser routedServicesUser : routedServicesUsers) {
                    routedServicesUser.addRoutedServices(serviceId, serviceRouteTable);
                }

                //TODO filters static services without URL
                removedRoutes = filterStaticRoutes(serviceId, staticServiceRoutes);
                removedRoutes.forEach(route -> discoveredRoutes.remove(route));

                //TODO adds new routes that are not present in the discoveryClientRouteLocatorRoutes
                for (String serviceRouteKey : serviceRouteKeys) {
                    if (!PatternMatchUtils.simpleMatch(ignoredServiceIds, serviceId) //service not ignored
                        && !discoveredRoutes.containsKey(serviceRouteKey) //route not in super.locateRoutes()
                        && !removedRoutes.contains(serviceRouteKey)) { //route not in removed routes

                        // Not ignored
                        discoveredRoutes.put(serviceRouteKey, new ZuulProperties.ZuulRoute(serviceRouteKey, serviceId));
                    }
                }
            }
        }

        LinkedHashMap<String, ZuulProperties.ZuulRoute> values = applyZuulPrefix(discoveredRoutes);

        return values;
    }

    private Set<String> filterStaticRoutes(String serviceId, Map<String, ZuulProperties.ZuulRoute> staticServiceRoutes) {
        Set<String> removedRoutes = new HashSet<>();
        if (staticServiceRoutes.containsKey(serviceId)
            && staticServiceRoutes.get(serviceId).getUrl() == null) {
            // Explicitly configured with no URL, they are the default routes from the parent
            // We need to remove them
            ZuulProperties.ZuulRoute staticRoute = staticServiceRoutes.get(serviceId);

            removedRoutes.add(staticRoute.getPath());
        }
        return removedRoutes;
    }

    private Map<String, ZuulProperties.ZuulRoute> extractStaticRoutes(LinkedHashMap<String, ZuulProperties.ZuulRoute> routesMap) {
        Map<String, ZuulProperties.ZuulRoute> staticServices = new LinkedHashMap<>();
        for (ZuulProperties.ZuulRoute route : routesMap.values()) {
            String serviceId = route.getServiceId();
            if (serviceId == null) {
                serviceId = route.getId();
            }
            if (serviceId != null) {
                staticServices.put(serviceId, route);
            }
        }
        return staticServices;
    }


    private LinkedHashMap<String, ZuulProperties.ZuulRoute> applyZuulPrefix(LinkedHashMap<String, ZuulProperties.ZuulRoute> routesMap) {
        LinkedHashMap<String, ZuulProperties.ZuulRoute> values = new LinkedHashMap<>();
        for (Map.Entry<String, ZuulProperties.ZuulRoute> entry : routesMap.entrySet()) {
            String path = entry.getKey();
            // Prepend with slash if not already present.
            if (!path.startsWith("/")) {
                path = "/" + path;
            }
            if (StringUtils.hasText(this.properties.getPrefix())) {
                path = this.properties.getPrefix() + path;
                if (!path.startsWith("/")) {
                    path = "/" + path;
                }
            }
            values.put(path, entry.getValue());
        }
        return values;
    }


    //TODO this fills the routes parameter, not great
    /**
     * Parse route keys from the metadata and populate service routes
     *
     * @param serviceInstances the list of service instances
     * @param routes          the service routes
     * @param serviceId       the service id
     * @return the list of route keys
     */
    @SuppressWarnings("squid:S3776") // Suppress complexity warning
    private List<String> createRouteKeys(List<ServiceInstance> serviceInstances,
                                         RoutedServices routes,
                                         String serviceId) {
        List<String> keys = new ArrayList<>();
        serviceInstances.stream()
            .map(ServiceInstance::getMetadata)
            .flatMap(
                metadata -> eurekaMetadataParser.parseToListRoute(metadata).stream()
            )
            .forEach(routedService -> {
                keys.add("/" + routedService.getGatewayUrl() + "/" + mapRouteToService(serviceId) + "/**");
                routes.addRoutedService(routedService);
            });

        return keys;
    }
}
