# rest-server-track
RESTful ServerTrack implemented in java using jetty, jersey and jaxp

BUILDING:

Requirements: java8 and maven3 - The magic of maven will Pull All The Things so the rest of the specific jars are in the pom.xml.

To build the project, open a terminal window, cd to the root of this directory and run 'mvn clean install'.
Should compile without errors or warnings.


RUNNING:

In the same terminal window and directory and run 'java -jar target/servertrack-1.0-SNAPSHOT-utzjohnliii.jar'.

NB: '1.0-SNAPSHOT-utzjohnliii' is defined in the project file pom.xml at the root of the directory. feel free to change it.

I have disabled running the unit tests as part of the build because as of today the unit tests expect this to already be
built and running :-}.

TESTING:

In the selfsame terminal window and directory, run 'mvn -Dtest=AppServerTrackTest -DskipTests=false test'.

A few words about the arguments to maven: they are mandatory; -Dtest identifies the test and -DskipTests=false undisables
the tests that where disabled in the pom.
