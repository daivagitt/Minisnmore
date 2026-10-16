package com.miniandmore.gateway.domain;

import java.math.BigDecimal;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Comparator;

public class AssertUtils {

    public static final Comparator<ZonedDateTime> zonedDateTimeSameInstant = Comparator.nullsFirst((e1, e2) ->
        e1.withZoneSameInstant(ZoneOffset.UTC).compareTo(e2.withZoneSameInstant(ZoneOffset.UTC))
    );

    public static final Comparator<BigDecimal> bigDecimalCompareTo = Comparator.nullsFirst(BigDecimal::compareTo);
}
