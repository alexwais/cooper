# Cooper

Co-location optimized elastic runner for microservices

Reference implementation/prototype of the CaaS middleware developed in the course of the corresponding master thesis at TU Wien / Distributed Systems Group.


### Prerequisites

* Java 11
* Maven
* CPLEX 12.10.0 installed (free academic version available at https://www.ibm.com/academic/technology/data-science)


### Build

First, we need to install the CPLEX JAR library in the local Maven repository for later use.

On macOS, the installation command would look like this:

```
$ mvn install:install-file \
    -DgroupId=cplex \
    -DartifactId=cplex \
    -Dversion=12.10.0 \
    -Dpackaging=jar \
    -Dfile=/Applications/CPLEX_Studio1210/cplex/lib/cplex.jar
```

Set the `-Dfile` path accordingly to point at the correct location of the cplex.jar file inside your CPLEX installation.


To build Cooper:

```
$ mvn clean install
```


### Run Cooper


The following command runs the compiled Cooper JAR with all necessary arguments set:

```
$ java -jar \
    -Djava.library.path=/Applications/CPLEX_Studio1210/cplex/bin/x86-64_osx \
    -Dscenario=a \
    -Dstrategy=ILP-NC \
    -Dmultiplicator=1 \
    target/Cooper.jar
```

* `-Djava.library.path`: Set this to the binary path of your CPLEX installation (sample for macOS). Required for CPLEX to work at runtime.
* `-Dscenario`: The scenario configuration to use.
* `-Dstrategy`: The optimization strategy applied. Available values: `ILP` | `ILP-NC` | `GA` | `GA-NC` | `FF`
* `-Dmultiplicator`: An integer multiplication factor for load fixture. `1` | `10` | `100`


### License

Apache License Version 2.0
