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

import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.netflix.zuul.filters.ZuulProperties;
import org.springframework.cloud.netflix.zuul.filters.discovery.DiscoveryClientRouteLocator;
import org.springframework.cloud.netflix.zuul.filters.discovery.ServiceRouteMapper;
import org.zowe.apiml.eurekaservice.client.util.EurekaMetadataParser;
import org.zowe.apiml.product.routing.RoutedServicesUser;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

public class NewApimlRouteLocator extends DiscoveryClientRouteLocator {
    private final DiscoveryClient discovery;
    private final ZuulProperties properties;
    private final List<RoutedServicesUser> routedServicesUsers;
    private final EurekaMetadataParser eurekaMetadataParser;

    NewApimlRouteLocator(String servletPath,
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

    @Override
    protected LinkedHashMap<String, ZuulProperties.ZuulRoute> locateRoutes() {
        LinkedHashMap<String, ZuulProperties.ZuulRoute> discoveryRoutes = super.locateRoutes();
        findDefaultRoutesFromParent(discoveryRoutes).forEach(key -> discoveryRoutes.remove(key));


        return discoveryRoutes;
    }

    private List<String> findDefaultRoutesFromParent(LinkedHashMap<String, ZuulProperties.ZuulRoute> routeMap) {
        List<String> result = new ArrayList<>();
        routeMap.forEach((key,entry) -> {if (entry.getUrl() == null) {result.add(key);} });
        return result;
    }
}
