package xyz.xenondevs.nova.ui.menu.explorer.recipes

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.ItemStack
import xyz.xenondevs.invui.gui.Gui
import xyz.xenondevs.invui.gui.PagedGui
import xyz.xenondevs.invui.gui.Structure
import xyz.xenondevs.invui.gui.TabGui
import xyz.xenondevs.invui.item.AbstractItem
import xyz.xenondevs.invui.item.AbstractPagedGuiBoundItem
import xyz.xenondevs.invui.item.AbstractTabGuiBoundItem
import xyz.xenondevs.invui.item.Click
import xyz.xenondevs.invui.item.ItemBuilder
import xyz.xenondevs.invui.item.ItemProvider
import xyz.xenondevs.invui.item.ItemWrapper
import xyz.xenondevs.invui.window.Window
import xyz.xenondevs.nova.resources.CharSizes
import xyz.xenondevs.nova.ui.menu.explorer.ItemMenu
import xyz.xenondevs.nova.ui.menu.explorer.recipes.group.RecipeGroup
import xyz.xenondevs.nova.util.component.adventure.font
import xyz.xenondevs.nova.util.component.adventure.move
import xyz.xenondevs.nova.util.component.adventure.moveToCenter
import xyz.xenondevs.nova.util.item.ItemUtils
import xyz.xenondevs.nova.util.playClickSound
import xyz.xenondevs.nova.world.item.DefaultGuiItems
import xyz.xenondevs.nova.world.item.recipe.RecipeContainer
import xyz.xenondevs.nova.world.item.recipe.RecipeRegistry
import java.util.*

typealias RecipeFilter = (RecipeContainer, RecipeGroup<*>) -> Boolean

private fun Player.showRecipes(id: String, recipes: Map<RecipeGroup<*>, Iterable<RecipeContainer>>?): Boolean {
    val info = RecipeRegistry.creationInfo[id]
    if (!recipes.isNullOrEmpty()) {
        RecipesWindow(this, "recipes:$id".hashCode(), recipes, info).show()
        return true
    } else if (info != null) {
        closeInventory()
        sendMessage(Component.translatable(info))
        return true
    }
    return false
}

fun Player.showRecipes(id: String): Boolean = showRecipes(id, RecipeRegistry.CREATION_RECIPES[id])

fun Player.showRecipes(item: ItemStack) = showRecipes(ItemUtils.getId(item))

fun Player.showRecipes(id: String, filter: RecipeFilter): Boolean = showRecipes(
    id,
    RecipeRegistry.CREATION_RECIPES[id]?.mapValues { entry ->
        entry.value.filter { filter(it, entry.key) }.toSet()
    }?.filter { it.value.isNotEmpty() }
)

fun Player.showRecipes(item: ItemStack, filter: RecipeFilter) = showRecipes(ItemUtils.getId(item), filter)

private fun Player.showUsages(id: String, recipes: Map<RecipeGroup<*>, Iterable<RecipeContainer>>?): Boolean {
    val info = RecipeRegistry.usageInfo[id]
    if (!recipes.isNullOrEmpty()) {
        RecipesWindow(this, "usages:$id".hashCode(), recipes, info).show()
        return true
    } else if (info != null) {
        closeInventory()
        sendMessage(Component.translatable(info))
        return true
    }
    return false
}

fun Player.showUsages(id: String): Boolean = showUsages(id, RecipeRegistry.USAGE_RECIPES[id])

fun Player.showUsages(item: ItemStack) = showUsages(ItemUtils.getId(item))

fun Player.showUsages(id: String, filter: RecipeFilter): Boolean = showUsages(
    id,
    RecipeRegistry.USAGE_RECIPES[id]?.mapValues { entry ->
        entry.value.filter { filter(it, entry.key) }.toSet()
    }?.filter { it.value.isNotEmpty() }
)

fun Player.showUsages(item: ItemStack, filter: RecipeFilter) = showUsages(ItemUtils.getId(item), filter)

/**
 * A menu that displays the given list of recipes.
 */
private class RecipesWindow(
    private val player: Player,
    private val id: Int,
    recipes: Map<RecipeGroup<*>, Iterable<RecipeContainer>>,
    info: String? = null
) : ItemMenu {

    private val recipesGuiStructure = Structure(
        "< . . . . . . . >",
        "x x x x x x x x x",
        "x x x x x x x x x",
        "x x x x x x x x x"
    )
        .addIngredient('<', ::PageBackItem)
        .addIngredient('>', ::PageForwardItem)

    private val viewerUUID = player.uniqueId

    private lateinit var currentType: RecipeGroup<*>

    private val mainGui: TabGui
    private lateinit var window: Window

    init {
        @Suppress("UNCHECKED_CAST")
        recipes as Map<RecipeGroup<Any>, Iterable<RecipeContainer>>

        val craftingTabs: List<Pair<RecipeGroup<*>, Gui>> = recipes
            .mapValues { (type, containers) -> createPagedRecipesGui(containers.map { container -> type.getGui(container.recipe) }) }
            .map { it.key to it.value }
            .sortedBy { it.first }

        mainGui = TabGui.normal()
            .setStructure(
                "b . . . . . . . .",
                "x x x x x x x x x",
                "x x x x x x x x x",
                "x x x x x x x x x",
                "x x x x x x x x x",
                ". . . . . . . . ."
            )
            .setTabs(craftingTabs.map { it.second })
            .addIngredient('b', LastRecipeItem(viewerUUID))
            .build()

        // Add tab buttons
        var lastTab = -1
        craftingTabs
            .map { it.first }
            .forEach { craftingType ->
                if (!::currentType.isInitialized) currentType = craftingType
                mainGui.setItem(2 + ++lastTab, CraftingTabItem(craftingType, lastTab))
            }

        if (info != null) mainGui.setItem(2 + ++lastTab, InfoItem(info))
    }

    override fun show() {
        ItemMenu.addToHistory(viewerUUID, this)
        window = Window.single {
            it.setViewer(player)
            it.setTitle(getCurrentTitle())
            it.setGui(mainGui)
        }.apply { open() }
    }

    private fun getCurrentTitle(): Component {
        val currentTab = mainGui.tabs[mainGui.tab] as PagedGui<*>
        val pageNumberString = "${currentTab.page + 1} / ${currentTab.pageAmount}"
        val pageNumberComponent = Component.text(pageNumberString, NamedTextColor.WHITE).font("nova:recipes_numbers")
        return Component.text()
            .append(currentType.texture.component)
            .moveToCenter()
            .move(CharSizes.calculateComponentWidth(pageNumberComponent) / -2)
            .append(pageNumberComponent)
            .build()
    }

    private fun updateTitle() {
        window.setTitle(getCurrentTitle())
    }

    override fun equals(other: Any?): Boolean {
        return other is RecipesWindow && id == other.id
    }

    override fun hashCode(): Int {
        return id
    }

    private inner class CraftingTabItem(private val recipeGroup: RecipeGroup<*>, private val tab: Int) :
        AbstractTabGuiBoundItem() {

        override fun getItemProvider(player: Player) = recipeGroup.icon

        override fun handleClick(clickType: ClickType, player: Player, click: Click) {
            if (clickType == ClickType.LEFT) {
                gui.tab = tab
                currentType = recipeGroup
                updateTitle()
            } else if (clickType == ClickType.RIGHT) {
                val recipes = RecipeRegistry.RECIPES_BY_TYPE[recipeGroup]
                if (recipes != null) RecipesWindow(
                    player,
                    "group:$recipeGroup".hashCode(),
                    mapOf(recipeGroup to recipes)
                ).show()
            }
        }

    }

    private class InfoItem(private val info: String) : AbstractItem() {

        override fun getItemProvider(player: Player): ItemBuilder =
            ItemBuilder(Material.KNOWLEDGE_BOOK)
                .setName(Component.translatable("menu.nova.recipe.item_info"))

        override fun handleClick(clickType: ClickType, player: Player, click: Click) {
            player.closeInventory()
            player.sendMessage(Component.translatable(info))
        }

    }

    private inner class PageBackItem : AbstractPagedGuiBoundItem() {

        override fun getItemProvider(player: Player) =
            (if (gui.hasPreviousPage()) DefaultGuiItems.TP_ARROW_LEFT_BTN_ON else DefaultGuiItems.TP_ARROW_LEFT_BTN_OFF)
                .clientsideProvider

        override fun handleClick(clickType: ClickType, player: Player, click: Click) {
            if (clickType == ClickType.LEFT && gui.hasPreviousPage()) {
                player.playClickSound()
                gui.goBack()
                updateTitle()
            }
        }

    }

    private inner class PageForwardItem : AbstractPagedGuiBoundItem() {

        override fun getItemProvider(player: Player) =
            (if (gui.hasNextPage()) DefaultGuiItems.TP_ARROW_RIGHT_BTN_ON else DefaultGuiItems.TP_ARROW_RIGHT_BTN_OFF)
                .clientsideProvider

        override fun handleClick(clickType: ClickType, player: Player, click: Click) {
            if (clickType == ClickType.LEFT && gui.hasNextPage()) {
                player.playClickSound()
                gui.goForward()
                updateTitle()
            }
        }

    }

    private fun createPagedRecipesGui(recipes: List<Gui>): Gui =
        PagedGui.guis()
            .setStructure(recipesGuiStructure)
            .setContent(recipes)
            .build()

}

private class LastRecipeItem(private val viewerUUID: UUID) : AbstractItem() {

    override fun getItemProvider(player: Player): ItemProvider {
        return if (ItemMenu.hasHistory(viewerUUID)) {
            DefaultGuiItems.TP_ARROW_LEFT_ON.clientsideProvider
        } else ItemWrapper(ItemStack(Material.AIR))
    }

    override fun handleClick(clickType: ClickType, player: Player, click: Click) {
        if (clickType == ClickType.LEFT && ItemMenu.hasHistory(viewerUUID)) ItemMenu.showPreviousMenu(viewerUUID)
    }

}
