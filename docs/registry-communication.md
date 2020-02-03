# Communication between Client, Discovery service, and Gateway

This document is a summary of the knowledge gathered during the implementation of PassTicket support. It includes an explanation of how to decrease delays in the registration process.

## Client and Discovery service

A client must register with the discovery server to begin communication. The following list presents the minimum required information that a client must provide:

- **serviceId (or applicationId)**
- **instanceId** 
    - Ensure that the instanceId is unique. 
    - The structure of the instanceId is typically `${hostname}:${serviceId}:${port}`. The third part can be also a random number or string.
 - **health URL**
    - The URL where the service responds with the status of the client
    - The discovery service can check this state but the state of registration is monitored through heartbeats.
 - **timeout of a heartbeat**
    - The client defines how often a heartbeat is sent and unregisters itself.
    - The client's active registration with the Discovery service is maintained until a heartbeat is not delivered within the timeout period or the client unregisters itself.  
 - **service location information**
    - Service location information is used for callback and includes `IP`, `hostname`, `port` for HTTP, or `securePort` for HTTPS.
    - `VipAddress` must be used and it is the same as the `serviceId`.
 - **information about environment (shortly AWS or own)**
 - **status**
    - In the registration, it replaces the first heartbeat
 - **other information** (Optional)
    - Other parameters can be included, but do not affect communication.
    - Customized data outside of the scope of Eureka can be stored in the metadata.
    - Note: Metadata is not data for one-time use. Matadata can be changed after registration. However, a REST method can be used to update these metadata. Other data cannot be changed after registration.

After registration, the client sends a heartbeat to the Discovery service. The heartbeat renews (extends) registration with the Discovery Server. When this heartbeat is not sent during the timeout period, the client is unregistered.

The client can drop communication by unregistering with the Discovery service. Unregistering the client speeds up the process of client removal. Without this call, the Discovery service waits for the heartbeat to time out. 
Note: This timeout is longer than the interval of the renewal of client registration through a heartbeat.

Typically, all communication is cached. As such, a client cannot be used immediately after registration. Caching occurs throughout the system so it takes some time to go through all service components. 
Typically, there is a thread pool whereby one thread periodically updates caches. All caches are independent and do not affect other caches.  

## Caching

The following section describes the usage of all caches. The main idea is to speed up the process of registration and deregistration. The description of the cache also includes a link to the solution.

###  ResponseCache in Discovery service

`ResponseCache` is implemented in the Discovery service. This cache is responsible for minimizing the call overhead of registered instances. When any application calls the Discovery service, initially the cache is used and a record is created for subsequent calls.

The default for this cache contains two spaces: `read` and `readWrite`. Application checks `read` and `readWrite`  whether the record is missing and, in the case that the record is missing, recreates a record. `Read` cache is updated by an internal thread which periodically compares records in both spaces (on references level, null including).

When values are different, records are copied from `readWrite` into `read` space.

The two spaces are evidently created for NetFlix purposes and are changed with pull request https://github.com/Netflix/eureka/pull/544. This improvement allows configuration to use only `readWrite` space, `read` will be ignored and the user looks directly to `readWrite`.

In the default setting (when `read` cache is on), Discovery service evicts records about service, delta, and full registry only in `readWrite` space on registration and deregistration. `read` still contains old records until the refresh thread is done.
Disabling `read` cache allows reading directly from `readWrite`. It removes delay there.

<font color = "red"> CHECK THIS STATEMENT FOR ACCURACY:


The following code disables the `read` cache: </font>

```
eureka:
    server:
        useReadOnlyResponseCache: false
```

###  Discovery client in Gateway


<font color = "red"> CHECK THIS FOR ACCURACY:

The Gateway contains a discovery client that supports queries about services and instances. the discovery client caches all the information from the Discovery service. Data are updated with a thread asynchronously, which periodically fetches new registries. These registries are updated either as delta, or full. </font>

the initial fetch is a full update fetch is as it is necessary to download all required information. After that, it is rarely used due to performance. 
The Gateway can call the full update, but it happens only if data are not correct to fix them. One possible reason could be a long delay between fetching.

Delta fetching loads just the last updates of the registry. Delta is not related to a specific Gateway and does not return differences since the last call. The Discovery service collects updates in the registry (client registration and cancellation) and stores them in a queue. This queue is sent to the Gateways. The Gateways detect what is new and update their own registry (one gateway can get the same information many times). The queue on the Discovery service contains information about changes for a while <font color = "red"> Can you be more specific than , "for a while"? </font> (the default is 180s <font color = "red"> what is being measured? 180 what? </font>). The queue is periodically cleaned by a different thread (another asynchronous task). This removes all information about changes older than the current configuration. For easy detection of new updates by the Gateway, there is a mechanism to store a version to each update (incrementing number). 

**solution**

This cache was minimized by allowing running asynchronous fetching at any time. The following classes are used:


<font color = "red"> Review the following for accuracy. </font>
- **ApimlDiscoveryClient**
    - This class customizes the implementation of discovery client
    - by reflection, <font color = "red"> WHAT DOES "BY REFLECTION MEAN? </font> it takes a reference to thread pool <font color = "red"> WHAT DOES "REFERENCE TO THREAD POOL" MEAN? </font> responsible for fetching the registry
    - contains the method ```public void fetchRegistry()```, which adds the new asynchronous command to fetch the registry
 - **DiscoveryClientConfig**
    - This class is a configuration bean to construct a custom discovery client
    - `DiscoveryClient` also support event, <font color = "red">SUPPORTS WHAT EVENT? </font> especially `CacheRefreshedEvent` after fetching
        - it is used to notify another bean to evict caches (route locators, ZUUL handle mapping, `CacheNotifier`)
 - **ServiceCacheController**
    - The controller to accept information about service changes (ie. new instance, removed instance)
    - This controller asks `ApimlDiscoveryClient` to fetch (it makes a delta and sends an event)
    - After this call, the process is asynchronous and cannot be directly checked (only by events).
    
###  Route locators in Gateway

The gateway includes the bean `ApimlRouteLocator`. This bean is responsible for collecting the client's routes. It indicates that information is available about the path and services. This information is required to map the URI to a service. The most important<font color = "red"> The most important what?</font> is the filter `PreDecorationFilter`. It calls the method ```Route getMatchingRoute(String path)``` on the locator to translate the URI into
information about the service. A filter then stores information about <font color = "red"> Information about what</font>(ie. `serviceId`) into the ZUUL context. 

In our implementation we use a custom locator, which adds information about static routing. It is possible to have multiple locators. All locators
could be collected by `CompositeRouteLocator`. Now `CompositeRouteLocator` contains `ApimlRouteLocator` and a default implementation. Implementation of static routing
could also be performed by a different locator (it is not necessary to override a locator based on `DiscoveryClient`). In a similar way a super class of 
`ApimlRouteLocator` uses `ZuulProperties`. This can be also be used to store a static route. 

**Note:** Replacing `ApimlRouteLocator` with multiple locators is only for information. This could be changed in the future.

**solution**

Evicted this bean. It is realized via the event <font color = "red"> Can you describe this event? What is this event?</font> from fetching registry (implemented in `DiscoveryClientConfig`) and
call on each locator method refresh(). This method call `discoveryClient` and then construct location mapping again. After 
fetching, a new version of the registry is constructed with new services.

### Gateway & ZuulHandlerMapping

<font color = "red"> This paragraph should be re-written for clarity. Check for accuracy. </font>

This bean serve method <font color = "red"> What is a "bean serve method" </font> detects endpoints and returns the right handler. Handlers are created at the start and then looked up
by the URI. In there <font color = "red"> In where?</font> is a mechanism of dirty data whereby handlers are created and are available (don't use locators) <font color = "red"> what does "don't use locators mean? Is this an instruction? </font> 
until they are marked as dirty. The next call refreshes all handlers by data from the locators.

**solution**

 A listener of the fetched registry is implemented by  `DiscoveryClientConfig` which marks `ZuulHandlerMapping` as dirty. <font color = "red"> Explain "dirty" </font>

### Ribbon load balancer

On the end of ZUUL is a load balancer. <font color = "red"> End of what?</font> For that <font color = "red">What is "that" in this context? </font> we use Ribbon (before speed up implementation it was `ZoneAwareLoadBalancer`).
Ribbon has also it's own cache. It is used to have information about instances. In brief, ZUUL gives Ribbon  a request and it should 
send <font color = "red"> Send what? </font> to an instance. ZUUL contains information about servers (`serviceId -> 1-N` instances) and information about the state of load
balancing. (depends on selected mechanism <font color = "red"> what depends onthe selected mechanism? </font> - a way to select the next instance). If this cache is not evicted, Ribbon can try to send a
request to the server which was removed, don't know any server to send or just overload an instance, because don't know about other. <font color = "red"> This is a long, run-on sentence that doesn't make sense. Please re-write. </font>
The Ribbon can throw many exceptions at this time, and it is not sure, that it retry sending in right way. <font color = "red"> The ribbon is not sure? Please explain. </font>

**solution**

Use `ApimlZoneAwareLoadBalancer`  to implement a load balancer. (This <font color = "red"> This what? </font> extends the original `ZoneAwareLoadBalancer`). This
implementation only adds the method ```public void serverChanged()``` which calls the super class to reload information about servers, specificlly the instances and their addresses.

The method `serverChanged` is called from `ServiceCacheEvictor` to be sure, that before custom `EhCaches` are evicted and the load balancer gets the right 
information from ZUUL. <font color = "red"> This sentence makes no sense structurally. Please re-write. </font>

### Service cache - our custom EhCache

For own purpose <font color = "red"> for what's own purpose? </font> was added `EhCache`, which can collect information about processes. It is highly recommended to synchronize
the state of `EhCache` with the discovery client. If the state is not synchronized, it is possible to use old values (ie. before registering the new service's 
instance with different data than the old one). Non-synchronized states can cause many problems in logic (based on the race condition).


<font color = "red"> Check this paragraph for accuracy. </font>

`CacheServiceController` was added to address this synchronization problem. <font color = "red"> check for accuracy. </font> This controller is called from the discovery service, specifically from
`EurekaInstanceRegisteredListener` by the event `EurekaInstanceRegisteredEvent`. For cleaning caches, the gateway uses the interface
`ServiceCacheEvict`. As such, each bean can be called as the result of any changes in the registry and evict `EhCache` (or a different cache). 

The Controller evicts all custom caches via the interface `ServiceCacheEvict` and as `ApimlDiscoveryClient` to fetch a new registry. <font color = "red"> This sentence does not make sense structurally. </font>After
this, other beans are notified (see `CacheRefreshedEvent` from the discovery client).

<font color = "red"> CHeck this paragraph for accuracy. I am unclear if these are conditions that cause a failure, or steps which should be followed. </font>

While this mechanism works, it is not working properly due to one of the following conditions:

- The instance changed in the discovery client
- The gateway is notified, clean custom caches and ask for new registry fetching
- ZUUL accepts a new request and makes a cache (again with the old state) - **this is wrong**
- fetching of registry is done, evict all Eureka caches

Anew bean `CacheEvictor` was added to address this failure. 
 
#### CacheEvictor

This bean collects all calls from `CacheServiceController` and it is waiting for registry fetching. In this event, it will clean all
custom caches (via interface `ServiceCacheEvict`). In the end, custom caches are evicted twice (before Eureka parts <font color = "red"> Eureka parts? </font>
and after). It fully supported right state. <font color = "red"> What does "It fully supported right state" mean? </font>


## Other improvements

Implementation of this improvement <font color = "red"> What improvement? </font> wasn't just about caches, but discovery service contains one bug with notification. <font color = "red"> This sentence makes no sense structurally. </font>

### Event from InstanceRegistry

In Discovery service bean `InstanceRegistry` exists <font color = "red"> Exists what? </font>. This bean is called to register, renew, and unregister of service (client). 
Unfortunately, this bean also contains a problem. It notifies about newly registered instances before it registers them, in a
similar way as unregistering (cancellation) and renewing. It does not matter with regard to renewing  as this does not represent a change, but other makes problem for us. <font color = "red"> What is "other" in this sentence and what problem does it introduce </font>  We
can clean caches before update in `InstanceRegistry` happened. An issue has been created to address this topic:

```
#2659 Race condition with registration events in Eureka server
https://github.com/spring-cloud/spring-cloud-netflix/issues/2659
```

This issue takes a long time and it is not good wait for implementation, for this reason was implemented `ApimlInstanceRegistry`.
This bean replaces implementation and makes notification in the right order. It is via java reflection and it will be removed when
Eureka will be fixed. 

## Using caches and their evicting 

If you use anywhere a custom cache, implement  the interface `ServiceCacheEvict` to evict. It offers two methods:
- `public void evictCacheService(String serviceId)`
    - to evict only part of caches related to one service (multiple instances with same `serviceId`)
    - if there is no way how to evict only parts, you can evict all records
- `public void evictCacheAllService()`
    - to evict all records in the caches, which can have a relationship with any service
    - this method is only in very rare cases, only apply if it is impossible to get a `serviceId` (ie. wrong format of `instanceId`)

## Order to clean caches

From the Instance registry information is distributed in this order:

```
Discovery service > ResponseCache in discovery service > Discovery client in gateway > Route locators in gateway > ZUUL handler mapping
```

`EhCache` occurs after this chain as this point is the first time that new data can be cached. 

From the user point of view, the Ribbon load balancer cache occurs after the ZUUL handler mapping. <font color = "red"> Why does this make sense from the users point of view? </font>

---

**REST API**

It is possible to use a REST API, described at https://github.com/Netflix/eureka/wiki/Eureka-REST-operations.

