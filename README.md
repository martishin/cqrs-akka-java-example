# CQRS and Event Sourcing Example with Akka, Kotlin, and Cassandra

This project demonstrates how to build an app that uses **CQRS (Command Query Responsibility Segregation)** and **Event Sourcing** patterns using Kotlin, Akka, and Cassandra.   
It implements hotel reservation management functionality with separate commands for creating, updating, and deleting reservations, along with querying operations to fetch individual or all reservations.

## Running locally
1. Clone the repository
```bash
git clone https://github.com/martishin/cqrs-akka-kotlin-example.git
cd cqrs-akka-kotlin-example
```
2. Start the Cassandra database
```bash
docker-compose up cassandra
```
3. Build and run the application
```bash
./gradlew run
```
4. The server will be available at http://localhost:8080.

## Making API Requests
You can interact with the system using the following curl commands. You can also execute these REST calls using [Postman](https://www.postman.com/).
* Create a reservation
```bash
curl -X POST http://localhost:8080/reservations \
  -H "Content-Type: application/json" \
  -d '{
    "guestId": "guest123",
    "startDate": "2023-11-01",
    "endDate": "2023-11-05",
    "roomNumber": 101
  }'
```

* Update the reservation
```bash
curl -X PUT http://localhost:8080/reservations/{confirmationNumber} \
  -H "Content-Type: application/json" \
  -d '{
    "startDate": "2023-12-01",
    "endDate": "2023-12-05",
    "roomNumber": 102
  }'       
```
3. Delete the reservation
```bash
curl -X DELETE http://localhost:8080/reservations/{confirmationNumber}
```
4. Get all reservations
```bash
curl -X GET http://localhost:8080/reservations
```
5. Get the reservation
```bash
curl -X GET http://localhost:8080/reservations/{confirmationNumber}
```

## Testing
Run tests
```bash 
./gradlew test
```

## Technologies Used
* [Kotlin](https://kotlinlang.org/) and [coroutines](https://kotlinlang.org/docs/coroutines-overview.html) - modern JVM language with concise syntax and safety features
* [Akka](https://akka.io/) - actor-based concurrency framework
* [Ktor](https://ktor.io/) - Kotlin framework for building asynchronous servers and clients
* [Apache Cassandra](https://cassandra.apache.org/_/index.html) - distributed NoSQL database for scalability and high availability
* [Gradle](https://gradle.org/) - build automation tool
