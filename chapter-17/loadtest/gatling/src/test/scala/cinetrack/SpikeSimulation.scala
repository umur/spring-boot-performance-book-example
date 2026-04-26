// Gatling spike simulation — covered in 6.3.
//
// Uses heavisideUsers to drop a step-function load onto the service. This is
// what stressPeakUsers used to be — the new API is heavisideUsers(N).during(D).
// constantConcurrentUsers is FrontLine/Enterprise-only, so we don't use it here.
package cinetrack

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import scala.concurrent.duration._

class SpikeSimulation extends Simulation {

  val baseUrl = sys.env.getOrElse("BASE_URL", "http://localhost:8080")

  val httpProtocol = http
    .baseUrl(baseUrl)
    .acceptHeader("application/json")
    .contentTypeHeader("application/json")

  val readScenario = scenario("ReadDuringSpike")
    .exec(session => session.set("username", s"spike_${System.nanoTime()}"))
    .exec(http("Register")
      .post("/api/auth/register")
      .body(StringBody(
        """{"username":"#{username}","email":"#{username}@example.com","password":"Password1!"}"""))
      .check(jsonPath("$.token").saveAs("token")))
    .repeat(20) {
      exec(http("List")
        .get("/api/watchlogs")
        .header("Authorization", "Bearer #{token}")
        .check(status.in(200, 429)))
        .pause(200.millis)
    }

  setUp(
    readScenario.inject(
      // 30s warm-up at low rate so the JIT/cache stabilizes before the spike.
      rampUsers(20).during(30.seconds),
      // The actual spike: 100 users dropped onto the service over a 10s window.
      heavisideUsers(100).during(10.seconds),
      // Hold the spike for a minute to capture saturation behavior.
      nothingFor(60.seconds),
      // Recovery — measure how fast the service drains its queue.
      rampUsersPerSec(20).to(0).during(30.seconds),
    ).protocols(httpProtocol),
  ).assertions(
    // We allow degradation during the spike but require the run to complete
    // without exceeding 5% errors and without p99 blowing past 2s.
    global.responseTime.percentile4.lt(2000),
    global.failedRequests.percent.lt(5),
  )
}
