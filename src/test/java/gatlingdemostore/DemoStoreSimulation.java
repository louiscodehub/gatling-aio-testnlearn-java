package gatlingdemostore;

import io.gatling.javaapi.core.*;
import io.gatling.javaapi.http.HttpProtocolBuilder;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

public class DemoStoreSimulation extends Simulation {

    private static final String DOMAIN = "demostore.gatling.io";
    private static final HttpProtocolBuilder HTTP_PROTOCOL = http.baseUrl("https://"+DOMAIN);

    private static final int User_Count = Integer.parseInt(System.getProperty("USERS","5"));
    private static final Duration Ramp_Duration = Duration.ofSeconds(Integer.parseInt(System.getProperty("RAMP_DURATION","10")));
    private static final Duration Test_Duration = Duration.ofSeconds(Integer.parseInt(System.getProperty("TOTAL_DURATION","60")));

    @Override
    public void before() {
        System.out.printf("Running test with %d users%n", User_Count);
        System.out.printf("Ramping users over %d seconds%n", Ramp_Duration.getSeconds());
        System.out.printf("Total test duration: %d seconds%n", Test_Duration.getSeconds());
    }

    @Override
    public void after() {
        System.out.println("Stress testing complete");
    }
    private static final FeederBuilder<String> CategoryFeeder =
            csv("data/ProductCategories.csv").random();
    private static final  FeederBuilder<Object> JsonFeederProducts =
            jsonFile("data/ProductDetails.json").random();
    private static final FeederBuilder<String> Credentials =
            csv("data/Credentials.csv").circular();

    private static final ChainBuilder initSession =
            exec(flushCookieJar())
                    .exec(session -> session.set("RandomNumber", ThreadLocalRandom.current().nextInt()))
                    .exec(session -> session.set("CustomerLoggedIn",false))
                    .exec(session -> session.set("CartTotal",0.00))
                    .exec(addCookie(Cookie("Sessionid",SessionID.random()).withDomain(DOMAIN)));



private static class PAGES {
  private static final ChainBuilder Homepage =
          exec(
                  http("Load Home Page")
                          .get("/")
                          .check(regex("<title>Gatling Demo-Store</title>").exists())
                          .check(css("#_csrf", "content").saveAs("csrf_value")));

  private static final ChainBuilder AboutUs =
          exec(
                  http("About-us Page")
                          .get("/about-us")
                          .check(substring("About Us")));
}
private static class Catalog{
  private static class Category{
    private static final ChainBuilder ViewCategory =
            feed(CategoryFeeder)
                    .exec(
                     http("Load Category Page - #{ProductName}")
                     .get("/category/#{ProductSlug}")
                             .check(css("#CategoryName").isEL("#{ProductName}")));
  }

  private static class Products{
    private static final ChainBuilder ViewProductPage =
            feed(JsonFeederProducts)
                    .exec(
                    http("Load Product Page -#{name}")
                    .get("/product/#{slug}")
                            .check(css("#ProductDescription").isEL("#{description}")));

    private static final  ChainBuilder AddProducts =
            exec(ViewProductPage)
                    .exec(
                    http("Add Product to Cart")
                            .get("/cart/add/#{id}")
                            .check(substring("items in your cart")))
                    .exec(session -> {
                        double CurrentCartTotal = session.getDouble("CartTotal");
                        double ItemPrice = session.getDouble("price");
                        return session.set("CartTotal",(CurrentCartTotal + ItemPrice));
                    });
  }
}
private static class Customer{
  private static final ChainBuilder Login =
          feed(Credentials)
                  .exec(
                  http("Login Page")
                          .get("/login")
                          .check(substring("Username:")))
                  .exec(session -> {
                      System.out.println("CustomerLoggedIn:" +session.get("CustomerLoggedIn").toString());
                      return session;
                        }
                  )
                  .exec(
                          http("Login User")
                                  .post("/login")
                                  .formParam("_csrf", "#{csrf_value}")
                                  .formParam("username", "#{UserName}")
                                  .formParam("password", "#{Password}"))
                  .exec(session -> session.set("CustomerLoggedIn", true))
                  .exec(session -> {
                      System.out.println("CustomerLoggedIn:" +session.get("CustomerLoggedIn").toString());
                     return session;
                       }
                 );
 }
private static class Checkout{
  private static final  ChainBuilder ViewCart =
          doIf(session -> !session.getBoolean("CustomerLoggedIn"))
                  .then(exec(Customer.Login))
                  .exec(
                    http("View Cart")
                    .get("/cart/view")
                    .check(css("#grandTotal").isEL("$#{CartTotal}")))
                  .exec(session -> {
                    System.out.println("CartTotal:" +session.get("CartTotal").toString());
                      return session;
                });
  private static final ChainBuilder Logout =
          exec(
               http("Checkout")
               .get("/cart/checkout")
                       .check(substring("Thanks for your order! See you soon!")));

 }

  private static final ScenarioBuilder scn = scenario("DemoStoreSimulation")
          .exec(initSession)
          .exec(PAGES.Homepage)
          .pause(2)
          .exec(PAGES.AboutUs)
          .pause(2)
          .exec(Catalog.Category.ViewCategory)
          .pause(2)
          .exec(Catalog.Products.AddProducts)
          .pause(2)
          .exec(Checkout.ViewCart)
          .pause(2)
          .exec(Checkout.Logout);

private static class UserTransactionFlows{
    private static final Duration MIN_PAUSE = Duration.ofMillis(100);
    private static final Duration MAX_PAUSE = Duration.ofMillis(500);

    private static final ChainBuilder BrowseStore =
            exec(initSession)
                    .exec(PAGES.Homepage)
                    .pause(MAX_PAUSE)
                    .exec(PAGES.AboutUs)
                    .pause(MIN_PAUSE, MAX_PAUSE)
                    .repeat(5)
                    .on(
                            exec(Catalog.Category.ViewCategory)
                            .pause(MIN_PAUSE, MAX_PAUSE)
                            .exec(Catalog.Products.ViewProductPage)
                    );

    private static final ChainBuilder AbandonCart =
            exec(initSession)
                    .exec(PAGES.Homepage)
                    .pause(MAX_PAUSE)
                    .exec(Catalog.Category.ViewCategory)
                    .pause(MIN_PAUSE, MAX_PAUSE)
                    .exec(Catalog.Products.ViewProductPage)
                    .pause(MIN_PAUSE, MAX_PAUSE)
                    .exec(Catalog.Products.AddProducts);

    private static final ChainBuilder CompletePurchase =
            exec(initSession)
                    .exec(PAGES.Homepage)
                    .pause(MAX_PAUSE)
                    .exec(Catalog.Category.ViewCategory)
                    .pause(MIN_PAUSE, MAX_PAUSE)
                    .exec(Catalog.Products.ViewProductPage)
                    .pause(MIN_PAUSE, MAX_PAUSE)
                    .exec(Catalog.Products.AddProducts)
                    .pause(MIN_PAUSE, MAX_PAUSE)
                    .exec(Checkout.ViewCart)
                    .pause(MIN_PAUSE, MAX_PAUSE)
                    .exec(Checkout.Logout);
}

private static class Scenarios {
    private static final ScenarioBuilder DefaultPurchase = scenario("E2E_Scenario")
            .during(Duration.ofSeconds(60))
            .on(randomSwitch()
                    .on(
                            Choice.withWeight(75.0,exec(UserTransactionFlows.BrowseStore)),
                            Choice.withWeight(15,exec(UserTransactionFlows.AbandonCart)),
                            Choice.withWeight(10.0,exec(UserTransactionFlows.CompletePurchase))));

    private static final ScenarioBuilder HighPurchase = scenario("HighPurchase_Scenario")
            .during(Duration.ofSeconds(60))
            .on(randomSwitch()
                    .on(
                            Choice.withWeight(25.0,exec(UserTransactionFlows.BrowseStore)),
                            Choice.withWeight(25.0,exec(UserTransactionFlows.AbandonCart)),
                            Choice.withWeight(50.0,exec(UserTransactionFlows.CompletePurchase))));

}
/*
    {
        setUp(
                Scenarios.DefaultPurchase.injectOpen(rampUsers(User_Count).during(Ramp_Duration))
                        .protocols(HTTP_PROTOCOL));
    }
    //TO RUN TWO SCENARIOS SEQUENTIALLY
    {
        setUp(
                Scenarios.DefaultPurchase.injectOpen(rampUsers(User_Count).during(Ramp_Duration))
                        .protocols(HTTP_PROTOCOL)
                        .andThen( //TO RUN TWO SCENARIOS SEQUENTIALLY
                        Scenarios.HighPurchase.injectOpen(rampUsers(5).during(Duration.ofSeconds(10)))
                        .protocols(HTTP_PROTOCOL)
                        ));
    }
 */

    //TO RUN TWO SCENARIOS PARALLELLY
    {
        setUp(
                Scenarios.DefaultPurchase.injectOpen(rampUsers(User_Count).during(Ramp_Duration)),
                Scenarios.HighPurchase.injectOpen(rampUsers(2).during(Duration.ofSeconds(10))))
                .protocols(HTTP_PROTOCOL);
    }
    //Open Model Example
    /*
  {
	  setUp(scn.injectOpen(
              atOnceUsers(1),
              nothingFor(Duration.ofSeconds(5)),
              rampUsers(10).during(Duration.ofSeconds(20)),
              nothingFor(Duration.ofSeconds(10)),
              constantUsersPerSec(1).during(Duration.ofSeconds(20))))
              .protocols(HTTP_PROTOCOL);
  }
*/
    // Closed Model Example
    /*
    {
        setUp(scn.injectClosed(
                constantUsersPerSec(10).during(Duration.ofSeconds(20)),
                rampConcurrentUsers(1).to(5).during(Duration.ofSeconds(20))))
                .protocols(HTTP_PROTOCOL);
    }
    */
    // Throttling
    /*
    {
      setUp(scn.injectOpen(
              constantUsersPerSec(1).during(Duration.ofSeconds(3)))
              .protocols(HTTP_PROTOCOL)
              .throttle(
                      reachRps(10).in(Duration.ofSeconds(30)),
                      holdFor(Duration.ofSeconds(60)),
                      jumpToRps(20),
                      holdFor(Duration.ofSeconds(60))))
              .maxDuration(Duration.ofMinutes(3));
    }
    */
}
