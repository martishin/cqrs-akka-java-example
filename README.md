# cqrs-akka-kotlin-example

1. Create a Reservation
```
curl -X POST http://localhost:8080/reservations \
  -H "Content-Type: application/json" \
  -d '{
    "guestId": "guest123",
    "startDate": "2023-11-01",
    "endDate": "2023-11-05",
    "roomNumber": 101
  }'
```

2. Update a Reservation
```
curl -X PUT http://localhost:8080/reservations/{D6VBJPPJVT} \
  -H "Content-Type: application/json" \
  -d '{
    "startDate": "2023-12-01",
    "endDate": "2023-12-05",
    "roomNumber": 102
  }'       
```
3. Delete a Reservation
```
curl -X DELETE http://localhost:8080/reservations/{confirmationNumber}
```
4. Get All Reservations
```
curl -X GET http://localhost:8080/reservations
```
5. Get a Single Reservation
```
curl -X GET http://localhost:8080/reservations/{confirmationNumber}
```
