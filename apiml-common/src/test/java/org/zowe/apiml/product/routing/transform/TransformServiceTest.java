/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */

package org.zowe.apiml.product.routing.transform;

import org.zowe.apiml.product.gateway.GatewayClient;
import org.zowe.apiml.product.gateway.GatewayConfigProperties;
import org.zowe.apiml.product.routing.RoutedService;
import org.zowe.apiml.product.routing.RoutedServices;
import org.zowe.apiml.product.routing.ServiceType;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import static org.junit.Assert.assertEquals;

public class TransformServiceTest {

    private static final String UI_PREFIX = "ui";
    private static final String API_PREFIX = "api";
    private static final String WS_PREFIX = "ws";

    private static final String SERVICE_ID = "service";

    private GatewayClient gatewayClient;

    @Rule
    public final ExpectedException exception = ExpectedException.none();

    @Before
    public void setup() {
        GatewayConfigProperties gatewayConfigProperties = GatewayConfigProperties.builder()
            .scheme("https")
            .hostname("localhost")
            .build();
        gatewayClient = new GatewayClient(gatewayConfigProperties);
    }

    @Test
    public void givenHomePageAndUIRoute_whenTransform_thenUseNewUrl() throws URLTransformationException {
        String url = "https://localhost:8080/ui";

        RoutedServices routedServices = new RoutedServices();
        RoutedService routedService1 = new RoutedService(SERVICE_ID, UI_PREFIX, "/ui");
        RoutedService routedService2 = new RoutedService(SERVICE_ID, "api/v1", "/");
        routedServices.addRoutedService(routedService1);
        routedServices.addRoutedService(routedService2);

        TransformService transformService = new TransformService(gatewayClient);
        String actualUrl = transformService.transformURL(ServiceType.UI, SERVICE_ID, url, routedServices);

        String expectedUrl = String.format("%s://%s/%s/%s",
            gatewayClient.getGatewayConfigProperties().getScheme(),
            gatewayClient.getGatewayConfigProperties().getHostname(),
            UI_PREFIX,
            SERVICE_ID);
        assertEquals(expectedUrl, actualUrl);
    }

    @Test
    public void givenHomePage_whenRouteNotFound_thenThrowException() throws URLTransformationException {
        String url = "https://localhost:8080/u";

        RoutedServices routedServices = new RoutedServices();
        RoutedService routedService1 = new RoutedService(SERVICE_ID, UI_PREFIX, "/ui");
        RoutedService routedService2 = new RoutedService(SERVICE_ID, "api/v1", "/");
        routedServices.addRoutedService(routedService1);
        routedServices.addRoutedService(routedService2);

        TransformService transformService = new TransformService(gatewayClient);
        exception.expect(URLTransformationException.class);
        exception.expectMessage("Not able to select route for url https://localhost:8080/u of the service service. Original url used.");
        transformService.transformURL(ServiceType.UI, SERVICE_ID, url, routedServices);

    }

    @Test
    public void givenHomePageAndWSRoute_whenTransform_thenUseNewUrl() throws URLTransformationException {
        String url = "https://localhost:8080/ws";

        RoutedServices routedServices = new RoutedServices();
        RoutedService routedService1 = new RoutedService(SERVICE_ID, WS_PREFIX, "/ws");
        RoutedService routedService2 = new RoutedService(SERVICE_ID, "api/v1", "/");
        routedServices.addRoutedService(routedService1);
        routedServices.addRoutedService(routedService2);

        TransformService transformService = new TransformService(gatewayClient);
        String actualUrl = transformService.transformURL(ServiceType.WS, SERVICE_ID, url, routedServices);

        String expectedUrl = String.format("%s://%s/%s/%s",
            gatewayClient.getGatewayConfigProperties().getScheme(),
            gatewayClient.getGatewayConfigProperties().getHostname(),
            WS_PREFIX,
            SERVICE_ID);
        assertEquals(expectedUrl, actualUrl);
    }

    @Test
    public void givenHomePageAndAPIRoute_whenTransform_thenUseNewUrl() throws URLTransformationException {
        String url = "https://localhost:8080/api";

        RoutedServices routedServices = new RoutedServices();
        RoutedService routedService1 = new RoutedService(SERVICE_ID, API_PREFIX, "/api");
        RoutedService routedService2 = new RoutedService(SERVICE_ID, "api/v1", "/");
        routedServices.addRoutedService(routedService1);
        routedServices.addRoutedService(routedService2);

        TransformService transformService = new TransformService(gatewayClient);
        String actualUrl = transformService.transformURL(ServiceType.API, SERVICE_ID, url, routedServices);

        String expectedUrl = String.format("%s://%s/%s/%s",
            gatewayClient.getGatewayConfigProperties().getScheme(),
            gatewayClient.getGatewayConfigProperties().getHostname(),
            API_PREFIX,
            SERVICE_ID);
        assertEquals(expectedUrl, actualUrl);
    }


    @Test
    public void givenInvalidHomePage_thenThrowException() throws URLTransformationException {
        String url = "https:localhost:8080/wss";

        TransformService transformService = new TransformService(gatewayClient);

        exception.expect(URLTransformationException.class);
        exception.expectMessage("The URI " + url + " is not valid.");
        transformService.transformURL(null, null, url, null);
    }

    @Test
    public void givenEmptyGatewayClient_thenThrowException() throws URLTransformationException {
        String url = "https:localhost:8080/wss";

        GatewayClient emptyGatewayClient = new GatewayClient();
        TransformService transformService = new TransformService(emptyGatewayClient);

        exception.expect(URLTransformationException.class);
        exception.expectMessage("Gateway not found yet, transform service cannot perform the request");
        transformService.transformURL(null, null, url, null);
    }


    @Test
    public void givenHomePage_whenPathIsNotValid_thenThrowException() throws URLTransformationException {
        String url = "https://localhost:8080/wss";

        RoutedServices routedServices = new RoutedServices();
        RoutedService routedService1 = new RoutedService(SERVICE_ID, WS_PREFIX, "/ws");
        RoutedService routedService2 = new RoutedService(SERVICE_ID, "api/v1", "/");
        routedServices.addRoutedService(routedService1);
        routedServices.addRoutedService(routedService2);

        TransformService transformService = new TransformService(gatewayClient);

        exception.expect(URLTransformationException.class);
        exception.expectMessage("The path /wss of the service URL https://localhost:8080/wss is not valid.");
        transformService.transformURL(ServiceType.WS, SERVICE_ID, url, routedServices);
    }

    @Test
    public void givenEmptyPathInHomePage_whenTransform_thenUseNewUrl() throws URLTransformationException {
        String url = "https://localhost:8080/";

        RoutedServices routedServices = new RoutedServices();
        RoutedService routedService1 = new RoutedService(SERVICE_ID, WS_PREFIX, "/");
        RoutedService routedService2 = new RoutedService(SERVICE_ID, "api/v1", "/");
        routedServices.addRoutedService(routedService1);
        routedServices.addRoutedService(routedService2);

        TransformService transformService = new TransformService(gatewayClient);

        String actualUrl = transformService.transformURL(ServiceType.WS, SERVICE_ID, url, routedServices);
        String expectedUrl = String.format("%s://%s/%s/%s%s",
            gatewayClient.getGatewayConfigProperties().getScheme(),
            gatewayClient.getGatewayConfigProperties().getHostname(),
            WS_PREFIX,
            SERVICE_ID,
            "/");
        assertEquals(expectedUrl, actualUrl);
    }

    @Test
    public void givenServiceUrl_whenItsRoot_thenKeepHomePagePathSame() throws URLTransformationException {
        String url = "https://locahost:8080/test";
        RoutedServices routedServices = new RoutedServices();
        RoutedService routedService1 = new RoutedService(SERVICE_ID, UI_PREFIX, "/");
        RoutedService routedService2 = new RoutedService(SERVICE_ID, "api/v1", "/");
        routedServices.addRoutedService(routedService1);
        routedServices.addRoutedService(routedService2);

        TransformService transformService = new TransformService(gatewayClient);

        String actualUrl = transformService.transformURL(ServiceType.UI, SERVICE_ID, url, routedServices);
        String expectedUrl = String.format("%s://%s/%s/%s%s",
            gatewayClient.getGatewayConfigProperties().getScheme(),
            gatewayClient.getGatewayConfigProperties().getHostname(),
            UI_PREFIX,
            SERVICE_ID,
            "/test");
        assertEquals(expectedUrl, actualUrl);
    }

    @Test
    public void givenUrlContainingPathAndQuery_whenTransform_thenKeepQueryPartInTheNewUrl() throws URLTransformationException {
        String url = "https://locahost:8080/ui/service/login.do?action=secure";
        String path = "/login.do?action=secure";
        RoutedServices routedServices = new RoutedServices();
        RoutedService routedService1 = new RoutedService(SERVICE_ID, UI_PREFIX, "/ui/service");
        RoutedService routedService2 = new RoutedService(SERVICE_ID, "api/v1", "/");
        routedServices.addRoutedService(routedService1);
        routedServices.addRoutedService(routedService2);

        TransformService transformService = new TransformService(gatewayClient);

        String actualUrl = transformService.transformURL(ServiceType.UI, SERVICE_ID, url, routedServices);
        String expectedUrl = String.format("%s://%s/%s/%s%s",
            gatewayClient.getGatewayConfigProperties().getScheme(),
            gatewayClient.getGatewayConfigProperties().getHostname(),
            UI_PREFIX,
            SERVICE_ID,
            path);
        assertEquals(expectedUrl, actualUrl);
    }


}
