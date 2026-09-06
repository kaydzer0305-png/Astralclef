package com.ezquest.astralclef.recipes;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.Recipe;
import net.minecraft.recipe.RecipeManager;
import net.minecraft.recipe.RecipeType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Authoritative Ch0.5–1 item ids + {@link RecipeSpec} bindings for Create: Astral.
 * <p>
 * Prefer match by recipe <b>type + I/O</b> via {@link RecipeManager} — do not hard-depend
 * on guessed {@code kubejs:} auto-ids. Stub bind strings like {@code astralclef:bind/bronze_smith}
 * are resolved at runtime when a world/server is present.
 * <p>
 * Sourced from Astral kubejs ({@code smithing.js}, {@code shaped.js}, {@code smelting.js},
 * {@code blasting.js}, {@code pressing.js}, {@code compacting.js}, {@code mixing.js}).
 */
public final class Ch01RecipeBindings {
	private static final Logger LOGGER = LoggerFactory.getLogger("astralclef/ch01-bindings");

	// --- Item / fluid constants (Astral) ---

	public static final String BRONZE_INGOT = "createastral:bronze_ingot";
	public static final String BRONZE_SHEET = "createastral:bronze_sheet";
	public static final String ANDESITE_COMPOUND = "createastral:andesite_compound";
	public static final String ANDESITE_ALLOY = "create:andesite_alloy";
	public static final String ANDESITE_DUST = "techreborn:andesite_dust";
	public static final String GROUT = "tconstruct:grout";
	public static final String COMPOUND_MIXTURE = "kubejs:compound_mixture";

	public static final String COPPER_INGOT = "minecraft:copper_ingot";
	public static final String TIN_INGOT = "techreborn:tin_ingot";
	public static final String ANDESITE = "minecraft:andesite";
	public static final String ZINC_NUGGET = "create:zinc_nugget";
	public static final String ALLOY_NUGGETS_TAG = "#create:alloy_nuggets";
	public static final String CLAY_BALL = "minecraft:clay_ball";
	public static final String COBBLESTONE = "minecraft:cobblestone";
	public static final String ZINC_INGOT = "create:zinc_ingot";
	public static final String GRAVEL = "minecraft:gravel";

	// --- Bind placeholders (resolved via RecipeManager) ---

	public static final String BIND_BRONZE_SMITH = "astralclef:bind/bronze_smith";
	public static final String BIND_COMPOUND_SHAPED = "astralclef:bind/compound_shaped";
	public static final String BIND_COMPOUND_SMELT = "astralclef:bind/compound_smelt";
	public static final String BIND_COMPOUND_BLAST = "astralclef:bind/compound_blast";
	public static final String BIND_PRESS_DUST = "astralclef:bind/press_dust";
	public static final String BIND_COMPACT_ANDESITE = "astralclef:bind/compact_andesite";
	public static final String BIND_MIXER_COMPOUND = "astralclef:bind/mixer_compound_mixture";
	public static final String BIND_GROUT = "astralclef:bind/grout";

	/** One input slot: item id, optional {@code #tag}, and count. */
	public static final class IngredientRef {
		public final String itemOrTag;
		public final int count;

		public IngredientRef(String itemOrTag, int count) {
			this.itemOrTag = itemOrTag;
			this.count = Math.max(1, count);
		}

		public boolean isTag() {
			return itemOrTag != null && itemOrTag.startsWith("#");
		}

		public String bareId() {
			return isTag() ? itemOrTag.substring(1) : itemOrTag;
		}

		@Override
		public String toString() {
			return count + "x" + itemOrTag;
		}
	}

	/**
	 * Declared Astral recipe: type id + inputs + output.
	 * {@code resolvedRecipeId} filled when {@link #resolve} finds a live RecipeManager match.
	 */
	public static final class RecipeSpec {
		public final String bindId;
		public final String recipeType;
		public final List<IngredientRef> inputs;
		public final String outputId;
		public final int outputCount;
		private volatile Identifier resolvedRecipeId;

		public RecipeSpec(String bindId, String recipeType, List<IngredientRef> inputs,
				String outputId, int outputCount) {
			this.bindId = bindId;
			this.recipeType = recipeType;
			this.inputs = Collections.unmodifiableList(new ArrayList<>(inputs));
			this.outputId = outputId;
			this.outputCount = Math.max(1, outputCount);
		}

		public Optional<Identifier> resolvedId() {
			return Optional.ofNullable(resolvedRecipeId);
		}

		void setResolved(Identifier id) {
			this.resolvedRecipeId = id;
		}

		@Override
		public String toString() {
			return "RecipeSpec{bind=" + bindId + ", type=" + recipeType
					+ ", in=" + inputs + ", out=" + outputCount + "x" + outputId
					+ ", resolved=" + resolvedRecipeId + "}";
		}
	}

	private static final Map<String, RecipeSpec> BY_BIND = new LinkedHashMap<>();

	static {
		register(new RecipeSpec(
				BIND_BRONZE_SMITH,
				"minecraft:smithing",
				List.of(new IngredientRef(COPPER_INGOT, 1), new IngredientRef(TIN_INGOT, 1)),
				BRONZE_INGOT,
				1));
		register(new RecipeSpec(
				BIND_COMPOUND_SHAPED,
				"minecraft:crafting_shaped",
				List.of(
						new IngredientRef(ANDESITE, 3),
						new IngredientRef(ZINC_NUGGET, 3), // alt: #create:alloy_nuggets
						new IngredientRef(CLAY_BALL, 3)),
				ANDESITE_COMPOUND,
				1));
		register(new RecipeSpec(
				BIND_COMPOUND_SMELT,
				"minecraft:smelting",
				List.of(new IngredientRef(ANDESITE_COMPOUND, 1)),
				ANDESITE_ALLOY,
				1));
		register(new RecipeSpec(
				BIND_COMPOUND_BLAST,
				"minecraft:blasting",
				List.of(new IngredientRef(ANDESITE_COMPOUND, 1)),
				ANDESITE_ALLOY,
				1));
		register(new RecipeSpec(
				BIND_PRESS_DUST,
				"create:pressing",
				List.of(new IngredientRef(COBBLESTONE, 1)),
				ANDESITE_DUST,
				1));
		register(new RecipeSpec(
				BIND_COMPACT_ANDESITE,
				"create:compacting",
				List.of(new IngredientRef(ANDESITE_DUST, 4)),
				ANDESITE,
				1));
		// Early mixer: mixture fluid — NOT direct mixer→alloy for Ch01
		register(new RecipeSpec(
				BIND_MIXER_COMPOUND,
				"create:mixing",
				List.of(
						new IngredientRef(ANDESITE, 1),
						new IngredientRef(ZINC_NUGGET, 1),
						new IngredientRef(CLAY_BALL, 1)),
				COMPOUND_MIXTURE,
				1));
		register(new RecipeSpec(
				BIND_GROUT,
				"create:mixing",
				List.of(
						new IngredientRef(ANDESITE_ALLOY, 1),
						new IngredientRef(ZINC_INGOT, 1),
						new IngredientRef(GRAVEL, 8)),
				GROUT,
				8));
	}

	private Ch01RecipeBindings() {}

	private static void register(RecipeSpec spec) {
		BY_BIND.put(spec.bindId, spec);
	}

	public static Map<String, RecipeSpec> all() {
		return Collections.unmodifiableMap(BY_BIND);
	}

	public static Optional<RecipeSpec> byBind(String bindId) {
		if (bindId == null) {
			return Optional.empty();
		}
		return Optional.ofNullable(BY_BIND.get(bindId));
	}

	/** True when id is an astralclef:bind/* placeholder or known catalogue key. */
	public static boolean isBindId(String id) {
		return id != null && BY_BIND.containsKey(id);
	}

	public static ItemStack itemStack(String itemId, int count) {
		if (itemId == null || itemId.isEmpty() || itemId.startsWith("#")
				|| COMPOUND_MIXTURE.equals(itemId)) {
			return ItemStack.EMPTY;
		}
		Identifier id = Identifier.tryParse(itemId);
		if (id == null) {
			return ItemStack.EMPTY;
		}
		Item item = Registry.ITEM.get(id);
		Identifier got = Registry.ITEM.getId(item);
		if (!id.equals(got)) {
			return ItemStack.EMPTY; // missing mod item
		}
		return new ItemStack(item, Math.max(1, count));
	}

	public static ItemStack outputStack(RecipeSpec spec) {
		if (spec == null) {
			return ItemStack.EMPTY;
		}
		return itemStack(spec.outputId, spec.outputCount);
	}

	/** Flattened item stacks for INSERT (skips fluids / missing registry entries). */
	public static List<ItemStack> inputStacks(RecipeSpec spec) {
		if (spec == null) {
			return List.of();
		}
		List<ItemStack> out = new ArrayList<>();
		for (IngredientRef ref : spec.inputs) {
			if (ref.isTag()) {
				// Prefer zinc nugget as concrete stand-in for #create:alloy_nuggets
				ItemStack zinc = itemStack(ZINC_NUGGET, ref.count);
				if (!zinc.isEmpty()) {
					out.add(zinc);
				}
				continue;
			}
			ItemStack stack = itemStack(ref.itemOrTag, ref.count);
			if (!stack.isEmpty()) {
				out.add(stack);
			}
		}
		return out;
	}

	/**
	 * Resolve bind → live datapack/KubeJS recipe id by matching type + output (+ soft input check).
	 * Caches on the {@link RecipeSpec}.
	 */
	public static Optional<Identifier> resolve(MinecraftServer server, RecipeSpec spec) {
		if (spec == null) {
			return Optional.empty();
		}
		if (spec.resolvedRecipeId != null) {
			return Optional.of(spec.resolvedRecipeId);
		}
		if (server == null) {
			return Optional.empty();
		}
		RecipeManager manager = server.getRecipeManager();
		Identifier typeId = Identifier.tryParse(spec.recipeType);
		if (typeId == null) {
			return Optional.empty();
		}
		RecipeType<?> type = Registry.RECIPE_TYPE.get(typeId);
		Identifier typeGot = Registry.RECIPE_TYPE.getId(type);
		if (!typeId.equals(typeGot)) {
			LOGGER.debug("Recipe type not registered yet: {}", spec.recipeType);
			return Optional.empty();
		}

		Identifier best = null;
		try {
			@SuppressWarnings({"unchecked", "rawtypes"})
			Collection<? extends Recipe<?>> recipes =
					manager.listAllOfType((RecipeType) type);
			for (Recipe<?> recipe : recipes) {
				if (!outputMatches(recipe, spec)) {
					continue;
				}
				if (!inputsSoftMatch(recipe, spec)) {
					continue;
				}
				best = recipe.getId();
				break;
			}
		} catch (Throwable t) {
			LOGGER.debug("resolve listAllOfType failed for {}: {}", spec.recipeType, t.toString());
			best = scanAllRecipes(manager, typeId, spec);
		}

		if (best != null) {
			spec.setResolved(best);
			LOGGER.info("Resolved {} → {} (type {})", spec.bindId, best, spec.recipeType);
			return Optional.of(best);
		}
		LOGGER.debug("No RecipeManager match for {} yet (type={}, out={})",
				spec.bindId, spec.recipeType, spec.outputId);
		return Optional.empty();
	}

	public static Optional<Identifier> resolve(ServerWorld world, String bindId) {
		Optional<RecipeSpec> spec = byBind(bindId);
		if (spec.isEmpty() || world == null) {
			return Optional.empty();
		}
		return resolve(world.getServer(), spec.get());
	}

	public static Optional<Identifier> resolve(MinecraftServer server, String bindId) {
		return byBind(bindId).flatMap(spec -> resolve(server, spec));
	}

	/** Refresh all cached resolutions (datapack reload). */
	public static void refresh(MinecraftServer server) {
		for (RecipeSpec spec : BY_BIND.values()) {
			spec.setResolved(null);
			resolve(server, spec);
		}
	}

	private static Identifier scanAllRecipes(RecipeManager manager, Identifier typeId, RecipeSpec spec) {
		try {
			for (RecipeType<?> rt : Registry.RECIPE_TYPE) {
				Identifier rid = Registry.RECIPE_TYPE.getId(rt);
				if (!typeId.equals(rid)) {
					continue;
				}
				@SuppressWarnings({"unchecked", "rawtypes"})
				Collection<? extends Recipe<?>> recipes = manager.listAllOfType((RecipeType) rt);
				for (Recipe<?> recipe : recipes) {
					if (outputMatches(recipe, spec) && inputsSoftMatch(recipe, spec)) {
						return recipe.getId();
					}
				}
			}
		} catch (Throwable t) {
			LOGGER.debug("scanAllRecipes: {}", t.toString());
		}
		return null;
	}

	private static boolean outputMatches(Recipe<?> recipe, RecipeSpec spec) {
		try {
			// Fluid / non-item outputs (e.g. kubejs:compound_mixture) — match by type+inputs only
			if (COMPOUND_MIXTURE.equals(spec.outputId)) {
				return true;
			}
			ItemStack out = recipe.getOutput();
			if (out == null || out.isEmpty()) {
				return false;
			}
			Identifier got = Registry.ITEM.getId(out.getItem());
			Identifier want = Identifier.tryParse(spec.outputId);
			if (want == null) {
				return false;
			}
			return want.equals(got) && out.getCount() >= 1;
		} catch (Throwable t) {
			return false;
		}
	}

	/**
	 * Soft input check: every non-tag declared input must appear in some ingredient
	 * (or we accept when recipe has no inspectable ingredients — Create custom serializers).
	 */
	private static boolean inputsSoftMatch(Recipe<?> recipe, RecipeSpec spec) {
		List<Ingredient> ingredients;
		try {
			ingredients = recipe.getIngredients();
		} catch (Throwable t) {
			return true;
		}
		if (ingredients == null || ingredients.isEmpty()) {
			return true; // Create pressing/mixing often opaque via custom codec
		}
		for (IngredientRef ref : spec.inputs) {
			if (ref.isTag() || COMPOUND_MIXTURE.equals(ref.itemOrTag)) {
				continue;
			}
			ItemStack needle = itemStack(ref.itemOrTag, 1);
			if (needle.isEmpty()) {
				continue; // item not loaded — don't reject
			}
			boolean found = false;
			for (Ingredient ing : ingredients) {
				if (ing == null || ing.isEmpty()) {
					continue;
				}
				if (ing.test(needle)) {
					found = true;
					break;
				}
			}
			if (!found) {
				return false;
			}
		}
		return true;
	}

	/** Catalogue façade used by {@link KubeJsAwareCatalogue}. */
	public static KubeJsAwareCatalogue asCatalogue() {
		return new KubeJsAwareCatalogue() {
			@Override
			public void refresh() {
				// Server-less refresh clears caches; full resolve needs server
				for (RecipeSpec spec : BY_BIND.values()) {
					spec.setResolved(null);
				}
			}

			@Override
			public Optional<String> find(String recipeId) {
				if (recipeId == null) {
					return Optional.empty();
				}
				if (BY_BIND.containsKey(recipeId)) {
					RecipeSpec spec = BY_BIND.get(recipeId);
					return Optional.of(spec.resolvedId().map(Identifier::toString).orElse(recipeId));
				}
				for (RecipeSpec spec : BY_BIND.values()) {
					if (spec.resolvedId().map(id -> id.toString().equals(recipeId)).orElse(false)) {
						return Optional.of(recipeId);
					}
					if (spec.bindId.equals(recipeId)) {
						return Optional.of(recipeId);
					}
				}
				return Optional.empty();
			}
		};
	}
}
