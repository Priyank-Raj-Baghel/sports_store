# SportsStore (Spring Boot)
E-commerce site for a sports clothing brand — Spring Boot 3 + Thymeleaf + JPA (H2 by default, MySQL optional).

## Run
Needs only **Java 17+** (`java -version`).
- Windows:  `mvnw.cmd spring-boot:run`
- Mac/Linux: `./mvnw spring-boot:run`   (if Maven is installed you can use `mvn spring-boot:run`)

Open http://localhost:8080
Demo customer: demo@store.com / demo123 — Admin: admin@store.com / admin123 (http://localhost:8080/admin)

## MySQL instead of H2
Edit `src/main/resources/application-mysql.properties` (username/password), then:
`./mvnw spring-boot:run -Dspring-boot.run.profiles=mysql`

## Razorpay (real gateway, test mode)
1. Create a free account at razorpay.com, switch to **Test Mode**, then Settings → API Keys → Generate key.
2. Set the keys, then run (PowerShell):
   `$env:RAZORPAY_KEY_ID="rzp_test_xxxx"; $env:RAZORPAY_KEY_SECRET="xxxx"; .\mvnw.cmd spring-boot:run`
   (Mac/Linux: `RAZORPAY_KEY_ID=... RAZORPAY_KEY_SECRET=... ./mvnw spring-boot:run`)
3. Checkout → Pay online → the Razorpay popup opens. Test card 4111 1111 1111 1111 (any future expiry, any CVV) or UPI `success@razorpay`.
Without keys the app falls back to the local mock gateway.
