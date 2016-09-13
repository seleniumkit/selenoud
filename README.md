# Selenoud
Microservice acting like Selenium Hub, but launching Selenium nodes within Docker containers per session request.


## Quick Start Guide
1. Install [Docker](https://www.docker.com/).
2. Create ```/etc/selenoud/images.json``` file. This file maps browser versions to Docker containers. The file must have the following structure:                                                                                        
    ```json
    {
      "images": {
        "chrome": { "image": "selenium/node-chrome:latest", "path": "/wd/hub/", "shmSize": 268435456 },
        "chrome:50.0.2661.86": { "image": "selenium/node-chrome:latest" },
        "chrome:50.0": {"image": "selenium/node-chrome:latest" },
        "chrome:50": { "image": "selenium/node-chrome:latest" },
        "firefox": { "image": "selenium/node-firefox:latest" },
        "firefox:45.0.2": { "image": "selenium/node-firefox:latest" },
        "firefox:45": { "image": "selenium/node-firefox:latest" }
      },
      "environment": [
        "HUB_PORT_4444_TCP_ADDR=$hubHost",
        "HUB_PORT_4444_TCP_PORT=$hubPort",
        "REMOTE_HOST=$name",
        "SE_OPTS=-port $port -Djava.security.egd=file:/dev/random",
        "DISPLAY=:$port"
      ]
    }
    ```
    In this file:
    * `images` section specifies the list of used Docker images (every image will be pulled from repo upon start). 
        An optional ```path``` parameter allows you to specify base URL used to create Selenium sessions (e.g. ```/wd/hub/``` for standard Selenium server). 
        An optional ```shmSize``` parameter allows you to specify the `ShmSize` parameter of container.
    * `environment` section represents the array of environment variables, that will be passed to the launching docker container. 
    You can use the following variables here: `$hubHost`, `$hubPort`, `$name`, `$port`, that represent host of this service,
    port of this service, name of a launching container and port of a launching container.

3. Create ```/etc/selenoud/log4j.properties``` file. This is the logging configuration:
    ```
    # suppress inspection "UnusedProperty" for whole file
    log4j.rootLogger=INFO, logger, selenoud

    # Enable lines below for debug purposes
    #log4j.logger.ru.qatools.selenoud=TRACE
    #log4j.selenoud.ru.qatools.selenoud=TRACE

    # CONSOLE appender not used by default
    log4j.appender.logger=org.apache.log4j.ConsoleAppender
    log4j.appender.logger.layout=org.apache.log4j.PatternLayout
    log4j.appender.logger.layout.ConversionPattern=%d [%-10.10t] %-5p %c{1} - %m%n

    log4j.appender.selenoud=org.apache.log4j.DailyRollingFileAppender
    log4j.appender.selenoud.MaxFileSize=2GB
    log4j.appender.selenoud.File=/var/log/selenoud/selenoud.log
    log4j.appender.selenoud.bufferSize=5242880
    log4j.appender.selenoud.MaxBackupIndex=7
    log4j.appender.selenoud.layout=org.apache.log4j.PatternLayout
    log4j.appender.selenoud.layout.ConversionPattern=%d [%-30.30t] %-5p %-30.30c{1} - %m%n
    ```

4. Start Selenoud container:
    ```
    $ docker run \
        -v /etc/selenoud/:/etc/selenoud:ro \
        -v /var/log/selenoud/:/var/log/selenoud \
        -v /var/run/docker.sock:/var/run/docker.sock \
        --name selenoud \
        --privileged \
        --restart=always \
        --net host \
        --log-opt max-size=1g \
        --log-opt max-file=2 \
        -e JAVA_OPTS="-Dlog4j.configuration=file:/etc/selenoud/log4j.properties
          -DimagesFile=/etc/selenoud/images.json
          -Dlogs.dir=/var/log/selenoud/logs" \
        -d seleniumkit/selenoud:latest
    ```

5. Run your tests using the following Selenium URL:
    ```
    http://localhost:4444/wd/hub
    ```
6. See log files for each session in ```/var/log/selenoud/logs```.

## Configuration

Selenoud supports the following System properties:
```properties
host=localhost                                          # host to listen on (for node to connect to)
port=4444                                               # port to listen on
limit.threads=200                                       # how many threads will be used
limit.bodyLength=10485760                               # maximum request body size (bytes)
limit.count=0                                           # max containers (0 - unlimited)
limit.startupSec=60                                     # max node startup (sec)
limit.inactivitySec=120                                 # max node inactivity (sec)
limit.readTimeoutSec=60                                 # timeout to read from node (sec)
limit.connectTimeoutSec=10                              # timeout to connect to node (sec)
limit.watcherIntervalMs=5000                            # interval(ms) of freezed containers watcher
cloud.host=172.17.0.1                                   # host to connect
container.port=4455                                     # port to use inside container (when network is bridge)
docker.network=bridge                                   # docker network driver (default: bridge)
docker.endpoint=unix:///var/run/docker.sock             # default docker endpoint
docker.pull.enabled=false                               # should we pull image per request? 
log4j.configuration=file:/etc/selenoud/log4j.properties # config for log4j
imagesFile=/etc/selenoud/images.json                    # images config file
logsCollector=ru.qatools.selenoud.docker.ToFileDockerLogCollector # containers logs collector
logs.dir=/tmp/selenoud/logs                             # where to put all the containers logs
```

## Development
### Building code

```
$ ./gradlew clean build
```

Then you can find the distribution under `build/distributions` directory.

### Running
To start the basic app type:
```
$ ./gradlew run
```
To run Selenoud in production mode: 

```$ JAVA_OPTS="-Dport=4444 -Dthreads=1000" ./bin/selenoud```

### Building Docker image

```
$ ./gradlew clean build docker
```