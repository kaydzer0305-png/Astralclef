package com.ezquest.astralclef.quests;

/**
 * FTB Quests IDs for Create: Astral (pack Laskyyy/Create-Astral,
 * branch Astral-Experimental). Sourced from
 * {@code config/ftbquests/quests/chapters/6.snbt} (win chapter).
 * <p>
 * IDs are hex FTB Quests UIDs (file.getQuest(id)). Keep these in sync with the
 * pack SNBT when DevOS updates land. All quests below are from chapter 6
 * (filename {@code 6}, FTB chapter id {@code 7136A22A8137E1D1}).
 * <p>
 * Ch0.5–1 quest mapping for FTB “quests31” etc still needs explicit JEI/quest
 * dump from a live pack — placeholders until Research supplies the chapter_2
 * mapping. This file already covers the Ch6 win condition.
 */
public final class AstralQuests {
	private AstralQuests() {}

	/** Chapter 6 — Astral Singularity win quest (item: createastral:astral_singularity). */
	public static final String WIN_ASTRAL_SINGULARITY = "0938969824F4D4CB";
	/** Chapter 6 — dragon kill gate (kill minecraft:ender_dragon, rewards 128 xp nuggets). */
	public static final String DRAGON_KILL = "58B45DBA573A9FFB";
	/** Chapter 6 — prerequisite container (ender_plating + …). */
	public static final String ENDER_PLATING = "1D59FDF8FE9BFC8F";
	/** Chapter 6 — contained_end. */
	public static final String CONTAINED_END = "7E274C274E5E3A5C";
	/** Chapter 6 — ultramatter. */
	public static final String ULTRAMATTER = "3F559FAFA31FD081";
	/** Chapter 6 — yttr root_of_continuity. */
	public static final String ROOT_OF_CONTINUITY = "0AE79E624EA78861";
	/** Chapter 6 — observation holster. */
	public static final String POLISHED_HOLSTER = "46620FE1375C7674";

	/** All Chapter 6 quest ids (useful for progress % in status). */
	public static final String[] CHAPTER_6_ALL = {
			"220441E71254C532", "0AE79E624EA78861", "586DA028F2A79E2F", "47A50CF905A68E98",
			"710B656C18F55A2A", "155B0CF48A2CC893", "111B851BE26C9C43", "477A4A079EE4CB67",
			"453A225243C415F9", "006C1E587619553B", "1D59FDF8FE9BFC8F", "70528B6EB84817B1",
			"48172552E60243CC", "332C9CADA179D514", "0E9DEE59EF7AAF22", "06B974CE21252348",
			"05F3CDD240A00C45", "7E7069083F7E84EA", "21305A046B14B983", "658F1D4DCD082AD0",
			"543FB6A35A0382AE", "540C6BA867A9F2C7", "2F264FA51CB9971D", "07CB23B8118CE00A",
			"171116CC8CD6606A", "4B5C2A5E3337E88E", "2EC69DE42957F831", "7536EE4DB00E8100",
			"543FB6A35A0382AE", "79737BD306B433EC", "7A23938B0DEE0B74", "3BC1C47CA86C200F",
			"47FF7B656DBE52E3", "20777196D4CD5C6F", "232BF3750113B99D", "743D4A82982C2F4F",
			"11B6F1D5D17591E3", "028C2D7ACC01610A", "05E5820BA29375CE", "46620FE1375C7674",
			"133327AF8E6C3F92", "78E334E650E918E0", "135798C8E97B7D89", "3B570698D276A201",
			"7A23938B0DEE0B74", "3BC1C47CA86C200F", "47FF7B656DBE52E3", "20777196D4CD5C6F",
			"232BF3750113B99D", "743D4A82982C2F4F", "11B6F1D5D17591E3", "028C2D7ACC01610A",
			"05E5820BA29375CE", "46620FE1375C7674", "133327AF8E6C3F92", "7136A22A8137E1D1",
			"0938969824F4D4CB", "1D76A6E8BD6B7FD1", "43C9FDBABD7FB776", "756FF6156BDE6ECE",
			"58B45DBA573A9FFB", "7AB989E821F11BB2"
	};

	public static final String CHAPTER_6_FILE = "6";
	public static final String CHAPTER_6_TITLE_KEY = "{ftbquests.chapter.6.title}";

	/** Known pack chapter filenames for reference. */
	public static final String[] CHAPTER_FILES = {
			"assorted_goals", "chapter_2", "chapter_3", "chapter_4", "chapter_5",
			"6", "6_raow", "astral_signals", "astral_storage", "culinary_delights"
	};
}
