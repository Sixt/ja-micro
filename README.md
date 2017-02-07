# Java-micro Documentation #

## Introduction ##

Java-micro is a framework that allows developers to easily develop microservices.
The framework takes care of many of the concerns so that the developer can simply
focus on the functionality of his services instead.

Internal Sixt users: Detailed documentation can be
[found on Confluence](https://confluence.sixt.com/display/GOOR/Java+Service+Architecture)

### Functional Areas ###

* Simply build service as docker container or fat jar.
* Configuration from environment, command-line and external configuration services.
* Standardized json logging.
* Standardized metrics reporting
* Simple interface for calling endpoints on other services and handling errors from them.
* Client-side load-balancer
* Simple interface for a service to support health checks.
* Database migrations built-in.
* Simplified event-handling using kafka.
* Pluggable service registry to register and discover service instances.
* Compatible with Go Micro to allow choice of implementation language.
* Guice dependency injection for ease of implementation and testing scenarios.
* Components to create service integration testing scenarios.

## Output Artifacts ##

One can build a shadow jar (fat jar - all dependencies included), or a docker image. One might
use the shadow jar for developer testing. The shadow jar is a dependency of the docker image
tasks as well. To start a service in a debugger, use the `JettyServiceBase` as a main class.

## Configuration Handling ##

For any component in a service requiring configuration, it can simply get a `ServiceProperties`
object injected into it, and request the configuration properties from it. Properties are
located from three locations: command-line, environment variables, and configuration service.
They are applied in that order. The same property with a different value from a later source
overwrites the earlier source. Right now, the Sixt configuration service provides long-polling
so that each service can get real-time updates to changes of the configuration. The configuration 
provider is pluggable so other configuration sources can be sourced as well.
There are hooks to get real-time notifications of configuration changes.

## Logging ##

There is standardized logging in place. The log records are json objects, and `Marker`s can
be used to dynamically create properties on those objects.

## Metrics ##

There is standardized metric handling in place. This is heavily opinionated to Sixt's
infrastructure, and uses metrics formatted in a specific format and sent to an influx agent
to be reported back to a central influxdb cluster.  The metrics reporting is pluggable to 
support reporting metrics in any desired format to any desired destination.

## RpcClient and Error Handling ##

In order to allow a service to call other services, there is support to easily create `RpcClient`s.
An `RpcClient` is an object that abstracts an rpc endpoint of another service.  One can create an `RpcClient`
by using the `RpcClientFactory`. Once one has an `RpcClient`, calling another service endpoint is as simple
as calling the `callSynchronous` method (a future version of the framework will also
support asynchronous calls) with a protobuf request message, and it will return a protobuf response
message. The timeout policy, the retry policy, and the error-handling are all handled by the client. By default,
we have said that the default retry policy should be to retry a failed request one time, if the response is
retriable. In a future release, we will add support for time budgeting, where a client of a service can set
how much time is allowed to service the whole request. For now, we have a static policy
with a default timeout of 1000ms, and this can be customized per client. The `RpcCallException` class defines all of
our exception categories, their default retriable setting, and what the resulting HTTP status code is (when using
an HTTP transport). When one service calls another (client calling server), if a server throws an exception
during processing the response, the exception is transparently transported back to the client and can be
rethrown on the client-side (ignoring the retry aspect here).
Default RpcClient retry policy (1) can be overridden with setting 'rpcClientRetries'.
Default RpcClient timeout policy (1000ms) can be overridden with setting 'rpcClientTimeout'.

## Client-side Load-balancer ##

There is a client-side load-balancer in place behind the facade of the `RpcClient`.
This interacts with the service registry to track instances of services a service wants
to interact with, tracks their state of health in order to know whether the instances
should receive requests, and tracks the response results of each rpc call in order to
maintain 'circuit breakers' to limit exposure to non-healthy service instances.

## Health Checks ##

Every few seconds, the health state for a service instance is reported to the service
registry plugin. Using annotations, there is support for a service developer to easily
hook into health checking to mark a service instance as unhealhty. There is also an
interface to immediately change the health state of an instance.

## Database Migrations ##

There is currently support for Flyway database migrations, which supports many different
SQL databases. This support extends into carefully controlling the service lifecycle
and health checks. A future version should support basic migration support for DynamoDB
instances.

## Kafka Events ##

There are factories/builders in place to easily create publishers and subscribers for
topics in Kafka.

## Service Registry ##

The service registry is pluggable with different backends (consul, etc.) The purpose
to for each service instance (which may be coming up on a rather anonymous IP address
and random port) to register so that other instances can locate it. It is also by
extension, available for services to use to locate instances of other services.
The service information contains specially-formatted tags which contain information
about the format of the request and response messages for each endpoint, and other
tags.

## Go Micro Compatibility ##

Java-micro is meant to keep compatibility so that service developers can easily choose
between developing a service in Java or Go. Other languages can also be supported by
using the Go Micro sidecar.

## Dependency Injection ##

Dependency injection is heavily used in java-micro. It is strictly supporting Guice.

## Service Integration Testing ##

We heavily use automation at Sixt in our microservice projects. As part of the scrum
definition of done, we have said that all stories should have quality assurance
provided by automated service integration tests. What this means is that core infrastructure
dependencies (for example, on one service, this is consul, postgres and kafka) and the
service itself are started as containers under docker-compose. Additionally, to
eliminate the problem that would arise of starting containers for every Sixt service
dependency (and their dependencies, etc.), there exists a class called `ServiceImpersonator`
that can serve as a complete service mock, available in service registry and serving
real rpc requests. However, the developer maps the requests and responses instead
of a real instance serving those requests.

## Contributing ##

Contributions to the continued evolution of the framework are welcome. Please keep in 
mind that the primary focus is to keep the framework minimal. Backwards-compatibility 
and following semantic versioning is strictly required. For any major changes 
that you would like to propose, please discuss the design with Brian before-hand to 
avoid wasted efforts on incompatible changes.

### Who do I talk to? ###
Brian Krahmer / brian.krahmer@sixt.com
