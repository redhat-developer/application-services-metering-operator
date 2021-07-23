
package com.redhat;

import io.javaoperatorsdk.operator.Operator;
import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;
import javax.inject.Inject;

@QuarkusMain
public class ProductMeterOperator implements QuarkusApplication {

  @Inject Operator operator;

  @Inject
  Metering metering;

  public static void main(String... args) {
    Quarkus.run(ProductMeterOperator.class, args);
  }

  @Override
  public int run(String... args) throws Exception {
    operator.start();

    //TODO WHAT?! QUITE THE HACK!
    // Should probably move to being called as part of the controller?
    metering.setup();

    Quarkus.waitForExit();

    metering.shutdown();

    return 0;
  }
}
