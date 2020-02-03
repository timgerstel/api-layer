# Communication between Client, Discovery service, and Gateway

This document is a summary of the knowledge gathered during the implementation of PassTicket support. It includes an explanation of how to decrease delays in the registration process.

## Client and Discovery service

A client must register with the discovery server to begin communication. Minimum required information from a client:

- **serviceId (or applicationId)**
- **instanceId** 
    - Ensure that the instanceId is unique. 
    - The structure of the instanceId is typically `${hostname}:${serviceId}:${port}`. The third part can be also a random number or string.
 - **health URL**
    - The URL where the service responds with the status of a client
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
    - Note: Metadata isn't data for one-time use, and can be changed after registration. However, a REST method can be used to update these metadata. Other data cannot be changed after registration

After registration, the client sends a heartbeat. Heartbeat renews (extends) registration with the Discovery Server. When it is not sent during the timeout period, the client is unregistered.

The client can drop communication by unregistering with the Discovery service. Unregistering the client speeds up the process of client removal. Without this call, the Discovery service waits for the heartbeat to time out. 
Note: This timeout is longer than the interval of the renewal of client registration through a heartbeat.

Typically, all communication is cached. As such, a client cannot be used immediately after registration. Caching occurs in many places of the system and it takes some time to go through all of them. 
Typically, there is a thread pool whereby one thread periodically updates caches. All caches are independent and do not affect other caches.  

## Caching

The following section describes the usage of all caches. The main idea is to speed up the process of registration and deregistration. The description of the cache also includes a link to the solution.

###  ResponseCache in Discovery service

`ResponseCache` is implemented in the Discovery service. This cache is responsible for minimizing the call overhead of registered instances. When any application calls the Discovery service, initially the cache is used and a record is created for subsequent calls.

The default for this cache contains two spaces: `read` and `readWrite`. Application checks `read` and `readWrite`  whether the record is missing and recreates a record in that case. `Read` cache is updated by an internal thread which periodically compares records in both spaces (on references level, null including).

When values are different, records are copied from `readWrite` into `read` space.

The two spaces are evidently created for NetFlix purposes and are changed with pull request https://github.com/Netflix/eureka/pull/544. This improvement allows configuration to use only `readWrite` space, `read` will be ignored and the user looks directly to `readWrite`.

In default setting (when `read` cache is on), Discovery service evicts on registration and deregistration records about service, delta, and full registry only in `readWrite` space. `read` still contains old record until refresh thread is done.
Disabling `read` cache allows reading directly from `readWrite`. It removes delay there.

```
eureka:
    server:
        useReadOnlyResponseCache: false
```

###  Discovery client in Gateway

The Gateway contains a discovery client that supports queries about services and instances. It caches all the information from the Discovery service. Data are updated with thread asynchronously. It periodically fetches new registries. They are updated either as delta, or full.

The full update fetch is the initial one as it is necessary to download all required information. After that, it is rarely used due to performance. 
The Gateway can call the full update, but it happens only if data are not correct to fix them. One possible reason could be a long delay between fetching.

Delta fetching loads just part of registry - the last updates. Delta is not related to specific Gateway, it doesn't return differences since the last call. The Discovery service collects updates in the registry (client registration and cancellation) and stores them into a queue. This queue is sent to Gateways. They detect what is new and update its own registry (one gateway can get the same information many times). The queue on the Discovery service contains information about changes for a while (the default is 180s). The queue is periodically cleaned by a different thread (another asynchronous task). This removes all information about changes older then configuration. For easy detection of new updates by Gateway, there is a mechanism to store version to each update (incrementing number).

**solution**

This cache was minimized by allowing running asynchronous fetching at any time. The following classes are used:

- **ApimlDiscoveryClient**
    - custom implementation of discovery client
    - by reflection, it takes a reference to thread pool responsible for fetching of registry
    - contains method ```public void fetchRegistry()```, which adds the new asynchronous command to fetch registry
 - **DiscoveryClientConfig**
    - configuration bean to construct custom discovery client
    - `DiscoveryClient` also support event, especially `CacheRefreshedEvent` after fetching
        - it is used to notify another bean to evict caches (route locators, ZUUL handle mapping, `CacheNotifier`)
 - **ServiceCacheController**
    - The controller to accept information about service changes (ie. new instance, removed instance)
    - This controller asks `ApimlDiscoveryClient` to fetch (it makes a delta and sends an event)
    - After this call, the process is asynchronous and cannot be directly checked (only by events).
    
###  Route locators in Gateway

The gateway includes the bean `ApimlRouteLocator`. This bean is responsible for collecting the client's routes. It indicates that information is available about the path and services. This information is required to map the URI to a service. The most important is the filter `PreDecorationFilter`. It calls the method ```Route getMatchingRoute(String path)``` on the locator to translate the URI into
information about the service. A filter then stores information about (ie. `serviceId`) into the ZUUL context. 

In our implementation we use a custom locator, which adds information about static routing. There is possible to have multiple locators. All of them 
could be collected by `CompositeRouteLocator`. Now `CompositeRouteLocator` contains `ApimlRouteLocator` and a default implementation. Implementation of static routing
could also be performed by a different locator (it is not necessary to override locator based on `DiscoveryClient`). In a similar way a super class of 
`ApimlRouteLocator` uses `ZuulProperties`. This can be also be used to store a static route. 

**Note:** To replace `ApimlRouteLocator` with multiple locators is only for information, and it could be changed in the future.

**solution**

Anyway this bean should be evicted. It is realized via event from fetching registry (implemented in DiscoveryClientConfig) and
call on each locator method refresh(). This method call discoveryClient and then construct location mapping again. Now after 
fetching new version of registry is constructed well, with new services.

### Gateway & ZuulHandlerMapping

This bean serve method to detect endpoint and return right handler. Handlers are created on the begin and then just looked up
by URI. In there is mechanism of dirty data. It means, that it create handlers and they are available (don't use locators) 
until they are mark as dirty. Then next call refresh all handlers by data from locators.

**solution**

In DiscoveryClientConfig is implemented listener of fetched registry. It will mark ZuulHandlerMapping as dirty.

### Ribbon load balancer

On the end of ZUUL is load balancer. For that we use Ribbon (before speed up implementation it was `ZoneAwareLoadBalancer`).
Ribbon has also own cache. It is used to have information about instances. Shortly, ZUUL give to Ribbon request and it should 
send to an instance. ZUUL contains information about servers (serviceId -> 1-N instances) and information about state of load
balancing (depends on selected mechanism - a way to select next instance). If this cache is not evicted, Ribbon can try send
request to server which was removed, don't know any server to send or just overload an instance, because don't know about other.
Ribbon can throw many exception in this time, and it is not sure, that it retry sending in right way.

**solution**

Now we use as load balancer implementation `ApimlZoneAwareLoadBalancer` (it extends original `ZoneAwareLoadBalancer`). This
implementation only add method ```public void serverChanged()``` which call super class to reload information about servers,
it means about instances and their addresses.

Method serverChanged is called from `ServiceCacheEvictor` to be sure, that before custom EhCaches are evicted and load balancer get right 
information from ZUUL.

### Service cache - our custom EhCache

For own purpose was added EhCache, which can collect many information about processes. It is highly recommended to synchronize
state of EhCache with discovery client. If not, it is possible to use old values (ie. before registering new service's 
instance with different data than old one). It can make many problems in logic (based on race condition).

It was reason to add `CacheServiceController`. This controller is called from discovery service (exactly from
`EurekaInstanceRegisteredListener` by event `EurekaInstanceRegisteredEvent`). For cleaning caches gateway uses interface
`ServiceCacheEvict`. It means each bean can be called about any changes in registry and evict EhCache (or different cache).

Controller evict all custom caches via interface `ServiceCacheEvict` and as `ApimlDiscoveryClient` to fetch new registry. After
than other beans are notified (see `CacheRefreshedEvent` from discovery client).

This mechanism is working, but not strictly right. There is one case:

1. instance changed in discovery client
2. gateway are notified, clean custom caches and ask for new registry fetching
3. ZUUL accepts new request and make a cache (again with old state) - **this is wrong**
4. fetching of registry is done, evict all Eureka caches

For this reason there was added new bean `CacheEvictor`.
 
#### CacheEvictor

This bean collects all calls from `CacheServiceController` and it is waiting for registry fetching. On this event it will clean all
custom caches (via interface `ServiceCacheEvict`). On the end it means that custom caches are evicted twice (before Eureka parts
and after). It fully supported right state.

## Other improvements

Implementation of this improvement wasn't just about caches, but discovery service contains one bug with notification. 

### Event from InstanceRegistry

In Discovery service bean `InstanceRegistry` exists. This bean is called for register, renew and unregister of service (client). 
Unfortunately, this bean contains also one problem. It notified about newly registered instances before it register it, in
similar way about unregister (cancellation) and renew. It doesn't matter about renew (it is not a change), but other makes problem for us. We
can clean caches before update in `InstanceRegistry` happened. On this topic exists issue:

```
#2659 Race condition with registration events in Eureka server
https://github.com/spring-cloud/spring-cloud-netflix/issues/2659
```

This issue takes long time and it is not good wait for implementation, for this reason was implemented `ApimlInstanceRegistry`.
This bean replace implementation and make notification in right order. It is via java reflection and it will be removed when
Eureka will be fixed. 

## Using caches and their evicting 

If you use anywhere custom cache, implement interface ServiceCacheEvict to evict. It offers two methods:
- `public void evictCacheService(String serviceId)`
    - to evict only part of caches related to one service (multiple instances with same serviceId)
    - if there is no way how to do it, you can evict all records
- `public void evictCacheAllService()`
    - to evict all records in the caches, which can have a relationship with any service
    - this method will be call very rare, only in case that there is impossible to get serviceId (ie. wrong format of instanceId)

## Order to clean caches

From Instance registry is information distributed in this order:

```
Discovery service > ResponseCache in discovery service > Discovery client in gateway > Route locators in gateway > ZUUL handler mapping
```

After this chain is our EhCache (because this is first time, which could cache new data)

From user point of view after ZUUL handler mapping exists Ribbon load balancer cache

---

**REST API**

```
There is possible to use REST API, described at https://github.com/Netflix/eureka/wiki/Eureka-REST-operations.
``` 
