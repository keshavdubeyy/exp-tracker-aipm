package com.yourteam.expensetracker.ui.theme

import androidx.compose.ui.graphics.vector.ImageVector
import com.yourteam.expensetracker.categoriser.Categories

/**
 * One Hugeicon per predefined category (DESIGN.md §4.10). Custom categories the user made fall
 * back to [tag]. Tinted by the Icon composable.
 */
object CategoryIcons {
    val tag: ImageVector get() = HugeIcons.Tag

    /** The icon for a category name. Predefined names map to their icon; anything else (custom) uses [tag]. */
    fun forCategory(category: String): ImageVector = when (category) {
        Categories.FOOD -> HugeIcons.Restaurant
        Categories.TRAVEL -> HugeIcons.Car
        Categories.SHOPPING -> HugeIcons.ShoppingBag
        Categories.GROCERIES -> HugeIcons.ShoppingBasket
        Categories.BILLS -> HugeIcons.Invoice
        Categories.HEALTH -> HugeIcons.Medicine
        Categories.ENTERTAINMENT -> HugeIcons.Ticket
        Categories.EDUCATION -> HugeIcons.Mortarboard
        Categories.PERSONAL_CARE -> HugeIcons.Perfume
        Categories.RENT -> HugeIcons.Building
        Categories.OTHERS -> HugeIcons.MoreCircle
        Categories.SALARY -> HugeIcons.Briefcase
        Categories.REFUND -> HugeIcons.ReturnRequest
        Categories.INTEREST -> HugeIcons.PercentCircle
        Categories.CASHBACK -> HugeIcons.Coins
        Categories.TRANSFERS -> HugeIcons.Transfer
        Categories.OTHER_INCOME -> HugeIcons.MoneyAdd
        else -> tag
    }
}
