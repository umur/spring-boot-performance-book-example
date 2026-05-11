// CinéTrack baseline Gatling simulation — covered in chapter 6.
//
// Same traffic shape as the k6 baseline: 30s ramp to 50 users, 2m steady,
// 30s ramp down. Numbers should land within ~10% of the k6 run; if they
// diverge significantly, the bottleneck is likely in the load generator,
// not the service under test (chapter 6.7 walks through that diagnosis).
//
// Run with: ./gradlew gatlingRun-cinetrack.BaselineSimulation
package cinetrack

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import scala.concurrent.duration._

class BaselineSimulation extends Simulation {

  val baseUrl = sys.env.getOrElse("BASE_URL", "http://localhost:8080")

  val httpProtocol = http
    .baseUrl(baseUrl)
    .acceptHeader("application/json")
    .contentTypeHeader("application/json")
    .userAgentHeader("Gatling/CinéTrack")

  // Each iteration of this scenario registers a fresh user (so it captures the
  // full register → login → write path, not just authenticated reads).
  val createWatchLog = scenario("CreateWatchLog")
    .exec(session => {
      val ts = System.nanoTime()
      session
        .set("username", s"gatling_u_$ts")
        .set("email", s"gatling_u_$ts@example.com")
        .set("tmdbId", 9000 + (ts % 1000).toInt)
    })
    .exec(http("Register")
      .post("/api/auth/register")
      .body(StringBody(
        """{"username":"#{username}","email":"#{email}","password":"Password1!"}"""))
      .check(status.is(201))
      .check(jsonPath("$.token").saveAs("token")))
    .pause(100.millis, 300.millis)
    .exec(http("CreateWatchLog")
      .post("/api/watchlogs")
      .header("Authorization", "Bearer #{token}")
      .body(StringBody(
        """{"tmdbId":#{tmdbId},"movieTitle":"Gatling Movie","watchedDate":"2024-01-01","rating":4}"""))
      .check(status.in(201, 409)))

  val readWatchLogs = scenario("ListWatchLogs")
    .exec(session => session.set("username", s"gatling_r_${System.nanoTime()}"))
    .exec(http("Register-ReadOnlyUser")
      .post("/api/auth/register")
      .body(StringBody(
        """{"username":"#{username}","email":"#{username}@example.com","password":"Password1!"}"""))
      .check(jsonPath("$.token").saveAs("token")))
    .repeat(10) {
      exec(http("ListWatchLogs")
        .get("/api/watchlogs")
        .header("Authorization", "Bearer #{token}")
        .check(status.is(200)))
        .pause(500.millis)
    }

  setUp(
    // 80% reads, 20% writes — same mix as the k6 baseline.
    readWatchLogs.inject(rampUsers(40).during(30.seconds)).protocols(httpProtocol),
    createWatchLog.inject(rampUsers(10).during(30.seconds)).protocols(httpProtocol),
  ).assertions(
    global.responseTime.percentile3.lt(500),    // p95
    global.responseTime.percentile4.lt(1000),   // p99
    global.failedRequests.percent.lt(1),
  )
}
