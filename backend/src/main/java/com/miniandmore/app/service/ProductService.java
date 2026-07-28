package com.miniandmore.app.service;

import com.miniandmore.app.domain.Product;
import com.miniandmore.app.repository.ProductRepository;
import com.miniandmore.app.service.dto.ProductDTO;
import com.miniandmore.app.service.mapper.ProductMapper;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Service Implementation for managing {@link com.miniandmore.app.domain.Product}.
 */
@Service
@Transactional
public class ProductService {

    private static final Logger LOG = LoggerFactory.getLogger(ProductService.class);

    private final ProductRepository productRepository;

    private final ProductMapper productMapper;

    private final ProductVariantSupport variantSupport;

    private final ProductColorImagesSupport colorImagesSupport;

    public ProductService(
        ProductRepository productRepository,
        ProductMapper productMapper,
        ProductVariantSupport variantSupport,
        ProductColorImagesSupport colorImagesSupport
    ) {
        this.productRepository = productRepository;
        this.productMapper = productMapper;
        this.variantSupport = variantSupport;
        this.colorImagesSupport = colorImagesSupport;
    }

    /**
     * Save a product.
     *
     * @param productDTO the entity to save.
     * @return the persisted entity.
     */
    public ProductDTO save(ProductDTO productDTO) {
        LOG.debug("Request to save Product : {}", productDTO);
        Product product = productMapper.toEntity(productDTO);
        normalizeVariants(product);
        product = productRepository.save(product);
        return productMapper.toDto(product);
    }

    /**
     * Update a product.
     *
     * @param productDTO the entity to save.
     * @return the persisted entity.
     */
    public ProductDTO update(ProductDTO productDTO) {
        LOG.debug("Request to update Product : {}", productDTO);
        Product product = productMapper.toEntity(productDTO);
        normalizeVariants(product);
        product = productRepository.save(product);
        return productMapper.toDto(product);
    }

    /**
     * Partially update a product.
     *
     * @param productDTO the entity to update partially.
     * @return the persisted entity.
     */
    public Optional<ProductDTO> partialUpdate(ProductDTO productDTO) {
        LOG.debug("Request to partially update Product : {}", productDTO);

        return productRepository
            .findById(productDTO.getId())
            .map(existingProduct -> {
                productMapper.partialUpdate(existingProduct, productDTO);
                normalizeVariants(existingProduct);
                return existingProduct;
            })
            .map(productRepository::save)
            .map(productMapper::toDto);
    }

    private void normalizeVariants(Product product) {
        if (product == null) {
            return;
        }
        Map<String, List<String>> galleryMap = colorImagesSupport.galleryMapFromColorsJson(product.getColorsJson());
        if (galleryMap.isEmpty()) {
            galleryMap = colorImagesSupport.parseMap(product.getColorImagesJson());
        }

        String structure = product.getItemStructure();
        if (structure == null || structure.isBlank()) {
            product.setItemStructure(ProductVariantSupport.STRUCTURE_SIMPLE);
            structure = ProductVariantSupport.STRUCTURE_SIMPLE;
        }
        if (variantSupport.isVariantsStructure(structure)) {
            if (!variantSupport.applyMatrixToProduct(product, structure, product.getVariantsJson())) {
                throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid variants matrix JSON for VARIANTS product"
                );
            }
            if (!galleryMap.isEmpty()) {
                product.setColorsJson(colorImagesSupport.applyGalleryMapToColorsJson(product.getColorsJson(), galleryMap));
            }
        } else {
            product.setItemStructure(ProductVariantSupport.STRUCTURE_SIMPLE);
            product.setVariantsJson(null);
        }

        colorImagesSupport.syncProductColorImages(product);
        colorImagesSupport.ensureProductImagesFromColorGalleries(product);
    }

    /**
     * Get all the products with eager load of many-to-many relationships.
     *
     * @return the list of entities.
     */
    public Page<ProductDTO> findAllWithEagerRelationships(Pageable pageable) {
        return productRepository.findAllWithEagerRelationships(pageable).map(productMapper::toDto);
    }

    /**
     * Get one product by id.
     *
     * @param id the id of the entity.
     * @return the entity.
     */
    @Transactional(readOnly = true)
    public Optional<ProductDTO> findOne(Long id) {
        LOG.debug("Request to get Product : {}", id);
        return productRepository.findOneWithEagerRelationships(id).map(productMapper::toDto);
    }

    /**
     * Delete the product by id.
     *
     * @param id the id of the entity.
     */
    public void delete(Long id) {
        LOG.debug("Request to delete Product : {}", id);
        productRepository.deleteById(id);
    }
}
