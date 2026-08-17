package com.miniandmore.app.service.mapper;

/**
 * Wires generated MapStruct {@code *MapperImpl} beans that depend on other mappers in unit tests.
 */
final class MapperTestSupport {

    private MapperTestSupport() {}

    static ProductMapper productMapper() {
        ProductMapperImpl mapper = new ProductMapperImpl();
        try {
            var field = ProductMapperImpl.class.getDeclaredField("categoryMapper");
            field.setAccessible(true);
            field.set(mapper, new CategoryMapperImpl());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to wire CategoryMapper into ProductMapperImpl", e);
        }
        return mapper;
    }
}
