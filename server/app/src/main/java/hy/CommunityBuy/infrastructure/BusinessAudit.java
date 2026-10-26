package hy.CommunityBuy.infrastructure;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface BusinessAudit {
  String value();

  int resourceArgument() default -1;

  int operationArgument() default -1;
}
