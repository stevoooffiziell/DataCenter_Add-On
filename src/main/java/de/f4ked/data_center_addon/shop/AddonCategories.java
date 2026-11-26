package de.f4ked.data_center_addon.shop;

import com.example.datacenter.shop.ShopCategory;

/**
 * The shop categories the add-on adds to Data Center's ({@code ShopCategoryMixin}, {@code ShopGroupMixin}).
 */
public final class AddonCategories {
    private static ShopCategory flux;

    private AddonCategories() {
    }

    /** The partner tab of Flux Networks; like every partner tab it only shows with the mod installed. */
    public static ShopCategory flux() {
        if (flux == null) {
            flux = ShopCategory.valueOf("FLUX");
        }
        return flux;
    }
}
