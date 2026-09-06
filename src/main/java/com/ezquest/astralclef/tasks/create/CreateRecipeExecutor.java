package com.ezquest.astralclef.tasks.create;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.ezquest.astralclef.recipes.Ch01RecipeBindings;
import com.ezquest.astralclef.tasks.create.world.CreateWorldContext;

import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Singleton executor for in-flight {@link CreateRecipeJob}s.
 * Holds {@link CreateWorldContext} and auto-binds from the first online player
 * when jobs need a world and context is unset.
 * <p>
 * When {@code recipeId} is an {@code astralclef:bind/*} key, resolves via
 * {@link Ch01RecipeBindings} and seeds INSERT stacks from the spec.
 */
public final class CreateRecipeExecutor {
	private static final Logger LOGGER = LoggerFactory.getLogger("astralclef/create-executor");
	private static final CreateRecipeExecutor INSTANCE = new CreateRecipeExecutor();

	/** kind + '\0' + recipeId → job */
	private final Map<String, CreateRecipeJob> jobs = new LinkedHashMap<>();
	private final CreateWorldContext worldContext = new CreateWorldContext();

	private CreateRecipeExecutor() {}

	public static CreateRecipeExecutor getInstance() {
		return INSTANCE;
	}

	private static String key(CreateRecipeKinds.Kind kind, String recipeId) {
		return kind.name() + '\0' + recipeId;
	}

	public CreateWorldContext getWorldContext() {
		return worldContext;
	}

	public void setWorldContext(ServerWorld world, BlockPos origin, int radius) {
		worldContext.set(world, origin, radius);
		LOGGER.info("Create world context set: {}", worldContext);
		if (world != null && world.getServer() != null) {
			Ch01RecipeBindings.refresh(world.getServer());
		}
	}

	public void clearWorldContext() {
		worldContext.clear();
	}

	public CreateRecipeJob start(CreateRecipeKinds.Kind kind, String recipeId) {
		Objects.requireNonNull(kind, "kind");
		Objects.requireNonNull(recipeId, "recipeId");
		String k = key(kind, recipeId);
		CreateRecipeJob existing = jobs.get(k);
		if (existing != null) {
			return existing;
		}
		CreateRecipeJob job = new CreateRecipeJob(kind, recipeId);
		seedFromBindings(job);
		jobs.put(k, job);
		LOGGER.info("Started Create recipe job: {} [{}]", recipeId, kind);
		return job;
	}

	private void seedFromBindings(CreateRecipeJob job) {
		Optional<Ch01RecipeBindings.RecipeSpec> spec = Ch01RecipeBindings.byBind(job.getRecipeId());
		if (spec.isEmpty()) {
			return;
		}
		MinecraftServer server = worldContext.isValid() ? worldContext.getWorld().getServer() : null;
		if (server != null) {
			Optional<Identifier> resolved = Ch01RecipeBindings.resolve(server, spec.get());
			resolved.ifPresent(id -> LOGGER.info("Job {} resolved to datapack id {}",
					job.getRecipeId(), id));
		}
		List<ItemStack> inputs = Ch01RecipeBindings.inputStacks(spec.get());
		if (!inputs.isEmpty()) {
			// Seed first concrete input; multi-insert handled across INSERT ticks later
			job.setPendingInsert(inputs.get(0).copy());
			job.setExpectedOutput(Ch01RecipeBindings.outputStack(spec.get()));
			job.setBindingInputs(inputs);
			LOGGER.info("Seeded job {} with {} input stack(s), expect {}",
					job.getRecipeId(), inputs.size(), spec.get().outputId);
		}
	}

	/** Advance jobs; auto-bind world context from first player when needed. */
	public void tick(MinecraftServer server) {
		if (jobs.isEmpty()) {
			return;
		}
		if (needsWorldContext()) {
			autoBindFromPlayers(server);
		}
		for (CreateRecipeJob job : jobs.values()) {
			if (!job.isDone()) {
				job.tick();
			}
		}
	}

	/** Back-compat no-arg tick (no auto-bind). Prefer {@link #tick(MinecraftServer)}. */
	public void tick() {
		if (jobs.isEmpty()) {
			return;
		}
		for (CreateRecipeJob job : jobs.values()) {
			if (!job.isDone()) {
				job.tick();
			}
		}
	}

	private boolean needsWorldContext() {
		if (worldContext.isValid()) {
			return false;
		}
		for (CreateRecipeJob job : jobs.values()) {
			if (!job.isDone()) {
				return true;
			}
		}
		return false;
	}

	private void autoBindFromPlayers(MinecraftServer server) {
		if (server == null) {
			return;
		}
		List<ServerPlayerEntity> players = server.getPlayerManager().getPlayerList();
		if (players.isEmpty()) {
			return;
		}
		ServerPlayerEntity player = players.get(0);
		ServerWorld world = (ServerWorld) player.getWorld();
		BlockPos origin = player.getBlockPos();
		worldContext.set(world, origin, CreateWorldContext.DEFAULT_SEARCH_RADIUS);
		Ch01RecipeBindings.refresh(server);
		LOGGER.info("Auto-bound Create world context from player {}: {}",
				player.getEntityName(), worldContext);
		// Late-seed any jobs that started before context existed
		for (CreateRecipeJob job : jobs.values()) {
			if (!job.isDone() && job.getPendingInsert().isEmpty() && Ch01RecipeBindings.isBindId(job.getRecipeId())) {
				seedFromBindings(job);
			}
		}
	}

	public CreateRecipeJob get(CreateRecipeKinds.Kind kind, String recipeId) {
		if (kind == null || recipeId == null) {
			return null;
		}
		return jobs.get(key(kind, recipeId));
	}

	public boolean isDone(CreateRecipeKinds.Kind kind, String recipeId) {
		CreateRecipeJob job = get(kind, recipeId);
		return job != null && job.isDone();
	}

	public boolean isSuccess(CreateRecipeKinds.Kind kind, String recipeId) {
		CreateRecipeJob job = get(kind, recipeId);
		return job != null && job.isDone() && job.isSuccess();
	}

	public String status(CreateRecipeKinds.Kind kind, String recipeId) {
		CreateRecipeJob job = get(kind, recipeId);
		if (job == null) {
			return "none";
		}
		if (job.isFailed()) {
			return "FAILED@" + job.getStep() + " ticks=" + job.getTickCount()
					+ " reason=" + job.getFailReason();
		}
		if (job.isDone() && job.isSuccess()) {
			return "DONE ticks=" + job.getTickCount()
					+ (job.getMachinePos() != null ? " at=" + job.getMachinePos().toShortString() : "");
		}
		String loc = job.getMachinePos() != null ? " at=" + job.getMachinePos().toShortString() : "";
		return job.getStep() + " ticks=" + job.getTickCount()
				+ " inStep=" + job.getTicksInStep() + loc
				+ " — " + job.describeStep();
	}

	public String statusSummary() {
		StringBuilder sb = new StringBuilder();
		sb.append("ctx=").append(worldContext).append("\n");
		if (jobs.isEmpty()) {
			sb.append("no create recipe jobs");
			return sb.toString();
		}
		sb.append(jobs.size()).append(" job(s):");
		for (CreateRecipeJob job : jobs.values()) {
			sb.append("\n  ").append(job.getKind()).append('/').append(job.getRecipeId())
					.append(" → ").append(status(job.getKind(), job.getRecipeId()));
		}
		return sb.toString();
	}

	public Collection<CreateRecipeJob> jobs() {
		return Collections.unmodifiableCollection(jobs.values());
	}

	public int clearFinished() {
		int removed = 0;
		Iterator<Map.Entry<String, CreateRecipeJob>> it = jobs.entrySet().iterator();
		while (it.hasNext()) {
			if (it.next().getValue().isDone()) {
				it.remove();
				removed++;
			}
		}
		return removed;
	}

	public boolean acceptOrProgress(CreateRecipeKinds.Kind kind, String recipeId) {
		CreateRecipeJob job = start(kind, recipeId);
		if (job.isFailed()) {
			return false;
		}
		if (job.isDone() && job.isSuccess()) {
			return true;
		}
		return true;
	}
}
