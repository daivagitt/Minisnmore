package com.miniandmore.app.domain;

import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

public class BlogPostTestSamples {

    private static final Random random = new Random();
    private static final AtomicLong longCount = new AtomicLong(random.nextInt() + (2L * Integer.MAX_VALUE));

    public static BlogPost getBlogPostSample1() {
        return new BlogPost().id(1L).slug("slug1").title("title1").coverUrl("coverUrl1");
    }

    public static BlogPost getBlogPostSample2() {
        return new BlogPost().id(2L).slug("slug2").title("title2").coverUrl("coverUrl2");
    }

    public static BlogPost getBlogPostRandomSampleGenerator() {
        return new BlogPost()
            .id(longCount.incrementAndGet())
            .slug(UUID.randomUUID().toString())
            .title(UUID.randomUUID().toString())
            .coverUrl(UUID.randomUUID().toString());
    }
}
