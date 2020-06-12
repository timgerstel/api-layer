# IntelliJ Idea setup

Guidelines relevant for development of the API Mediation Layer in the IntelliJ IDEA. 

## Code Development

- Enable _Annotations processing_ if you haven't done so already (Just go to settings and search for 'annotation')
- Install Lombok plugin. Go to the plugins in the setting and look for the lombok plugin. 
- Make sure that the Gradle JVM is set to the JDK 1.8. To set it go to the Settings->Build,Execution,Deployment->Build Tools->Gradle

## Running of the services

- Go to 'Services', it is available via alt+8 or on the bottom line of the IDEA.

For each of the available services:

1. Right click a service and select 'Edit Configuration' (or press F4 while the service is selected)
2. Clear all 'VM options' in the 'Environment' section
3. Then under the 'Override parameters' section add a new parameter `spring.config.additional-location` and its value `file:./config/local/{SERVICE_NAME}.yml` Replace SERVICE_NAME with the following:  
    1. ApiCatalogApplication - api-catalog-service
    2. DiscoverableClientSampleApplication - discoverable-client
    3. DiscoveryServiceApplication - discovery-service
    4. EnablerV1SampleApplication - onboarding-enabler-spring-v1-sample-app
    5. GatewayApplication - gateway-service
4. Run the service
