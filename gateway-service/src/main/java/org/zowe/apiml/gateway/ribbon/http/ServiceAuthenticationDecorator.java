/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */

package org.zowe.apiml.gateway.ribbon.http;

import com.netflix.appinfo.InstanceInfo;
import com.netflix.zuul.context.RequestContext;
import lombok.RequiredArgsConstructor;
import org.apache.http.HttpRequest;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.zowe.apiml.gateway.security.service.AuthenticationService;
import org.zowe.apiml.gateway.security.service.ServiceAuthenticationServiceImpl;
import org.zowe.apiml.gateway.security.service.schema.AuthenticationCommand;
import org.zowe.apiml.gateway.security.service.schema.ServiceAuthenticationService;
import org.zowe.apiml.security.common.auth.Authentication;

import static org.zowe.apiml.gateway.ribbon.ApimlZoneAwareLoadBalancer.LOADBALANCED_INSTANCE_INFO_KEY;
import static org.zowe.apiml.gateway.security.service.ServiceAuthenticationServiceImpl.AUTHENTICATION_COMMAND_KEY;

@RequiredArgsConstructor
public class ServiceAuthenticationDecorator {

    private final ServiceAuthenticationService serviceAuthenticationService;
    private final AuthenticationService authenticationService;

    private static final String INVALID_JWT_MESSAGE = "Invalid JWT token";

    public void process(HttpRequest request) throws RequestAbortException {
        final RequestContext context = RequestContext.getCurrentContext();

        if (context.get(AUTHENTICATION_COMMAND_KEY) != null && context.get(AUTHENTICATION_COMMAND_KEY) instanceof ServiceAuthenticationServiceImpl.UniversalAuthenticationCommand) {
            InstanceInfo info = getInstanceInfoFromContext(context);
            final Authentication authentication = serviceAuthenticationService.getAuthentication(info);
            boolean rejected = false;
            AuthenticationCommand cmd = null;

            try {
                final String jwtToken = authenticationService.getJwtTokenFromRequest(context.getRequest()).orElse(null);

                //what if cmd is null? it's failing with NullPointerException, if jwt is null cmd is null
                cmd = serviceAuthenticationService.getAuthenticationCommand(authentication, jwtToken);

                if (cmd != null && cmd.isRequiredValidJwt()) {
                    rejected = (jwtToken == null) || !authenticationService.validateJwtToken(jwtToken).isAuthenticated();
                }
            }
            catch (AuthenticationException ae) {
                rejected = true;
            }
            if (rejected) {
                throw new RequestAbortException(new BadCredentialsException(INVALID_JWT_MESSAGE));
            }

            cmd.applyToRequest(request);
        }
    }

    private InstanceInfo getInstanceInfoFromContext(RequestContext context) throws RequestContextNotPreparedException {
        Object o = context.get(LOADBALANCED_INSTANCE_INFO_KEY);
        if (o == null && ! (o instanceof InstanceInfo)) {
            throw new RequestContextNotPreparedException("InstanceInfo of loadbalanced instance is not present in RequestContext");
        }
        return (InstanceInfo) o;
    }
}

