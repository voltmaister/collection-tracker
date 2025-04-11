package com.voltmaister;

// Standard Java imports
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.concurrent.Executors;

// Java Swing imports
import javax.inject.Inject;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

// External library imports
import com.google.inject.Provides;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// RuneLite API imports
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.ImageUtil;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.ChatMessageType;
import net.runelite.client.game.ItemManager;
import net.runelite.api.IndexedSprite;
import net.runelite.client.util.AsyncBufferedImage;


// Project-specific imports
import com.voltmaister.api.TempleApiClient;
import com.voltmaister.config.CollectionTrackerConfig;
import com.voltmaister.data.CollectionItem;
import com.voltmaister.db.CollectionDatabase;
import com.voltmaister.parser.CollectionParser;





@PluginDescriptor(
		name = "Collection Tracker",
		description = "Tracks your collection log progress",
		tags = {"collection", "log", "tracker"}
)

public class CollectionTrackerPlugin extends Plugin
{
	private static final Logger log = LoggerFactory.getLogger(CollectionTrackerPlugin.class);

	@Inject private ItemManager itemManager;
	@Inject private ChatMessageManager chatMessageManager;
	@Inject private ClientToolbar clientToolbar;
	@Inject private Client client;
	@Inject private CollectionTrackerConfig config;

	private NavigationButton navButton;
	private PluginPanel panel;

	private final Map<Integer, Integer> itemIconIndexes = new HashMap<>();

	private int itemIconStartIndex = -1;

	private final JTextArea outputArea = new JTextArea();

	private int count;

	private final BufferedImage icon = loadIcon();
	private final Set<Integer> loadedItemIds = new HashSet<>();

	private BufferedImage loadIcon() {
		try {
			return ImageUtil.loadImageResource(getClass(), "/test.png");
		} catch (Exception e) {
			log.warn("Using fallback icon", e);
			BufferedImage fallback = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
			Graphics2D g = fallback.createGraphics();
			g.setColor(Color.RED);
			g.fillRect(0, 0, 16, 16);
			g.dispose();
			return fallback;
		}
	}

	@Override
	protected void startUp() throws Exception {
		log.info("Collection Tracker started!");

		CollectionDatabase.init();

		panel = new PluginPanel() {
		};
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBorder(new EmptyBorder(10, 10, 10, 10));
		panel.setBackground(new Color(43, 39, 35));

		// Title
		JLabel titleLabel = new JLabel("Collection Tracker", JLabel.CENTER);
		titleLabel.setForeground(Color.WHITE);
		titleLabel.setAlignmentX(JLabel.CENTER_ALIGNMENT);
		panel.add(titleLabel);

		// Buttons
		JPanel buttonPanel = new JPanel();
		buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.Y_AXIS));
		buttonPanel.setBackground(new Color(43, 39, 35));


		JButton printButton = new JButton("Print Collections");
		printButton.addActionListener(e -> printAllCollections());
		buttonPanel.add(printButton);

		JButton syncButton = new JButton("Sync Collection Log");
		syncButton.addActionListener(e -> syncCollectionLog());
		buttonPanel.add(syncButton);

		printButton.setAlignmentX(JButton.CENTER_ALIGNMENT);
		syncButton.setAlignmentX(JButton.CENTER_ALIGNMENT);

		buttonPanel.add(Box.createVerticalStrut(5)); // space between buttons


		panel.add(buttonPanel);

		outputArea.setEditable(false);
		outputArea.setLineWrap(true);
		outputArea.setWrapStyleWord(true);
		outputArea.setBackground(new Color(30, 30, 30));
		outputArea.setForeground(Color.WHITE);
		outputArea.setBorder(new EmptyBorder(10, 10, 10, 10));

		JScrollPane scrollPane = new JScrollPane(outputArea);
		scrollPane.setPreferredSize(new Dimension(0, 200));
		scrollPane.setMaximumSize(new Dimension(Integer.MAX_VALUE, 300));
		scrollPane.setBorder(BorderFactory.createEmptyBorder());
		scrollPane.getVerticalScrollBar().setUnitIncrement(16);

		panel.add(scrollPane);

		navButton = NavigationButton.builder()
				.tooltip("Collection Tracker")
				.icon(icon)
				.priority(5)
				.panel(panel)
				.build();

		clientToolbar.addNavigation(navButton);
	}


	@Override
	protected void shutDown() throws Exception
	{
		clientToolbar.removeNavigation(navButton);

		// 🧼 Clear cached icons and IDs to prevent memory buildup
		itemIconIndexes.clear();
		loadedItemIds.clear();

		log.info("Collection Tracker stopped!");
	}

	private void panelLog(String message) {
		outputArea.setText(message);
	}

	private void loadItemIcons(List<CollectionItem> items) {
		List<CollectionItem> newItems = new ArrayList<>();

		for (CollectionItem item : items) {
			if (!loadedItemIds.contains(item.getItemId())) {
				newItems.add(item);
				loadedItemIds.add(item.getItemId());
			}
		}

		if (newItems.isEmpty()) return;

		IndexedSprite[] modIcons = client.getModIcons();
		if (modIcons == null) return;

		int currentLength = modIcons.length;
		int newSize = currentLength + newItems.size();
		IndexedSprite[] newModIcons = Arrays.copyOf(modIcons, newSize);
		client.setModIcons(newModIcons);

		for (int i = 0; i < newItems.size(); i++) {
			CollectionItem item = newItems.get(i);
			int modIconIndex = currentLength + i;
			itemIconIndexes.put(item.getItemId(), modIconIndex);

			AsyncBufferedImage img = itemManager.getImage(item.getItemId());
			int finalIndex = modIconIndex;

			img.onLoaded(() -> {
				BufferedImage scaled = ImageUtil.resizeImage((BufferedImage) img, 18, 16);
				IndexedSprite sprite = ImageUtil.getImageIndexedSprite(scaled, client);
				client.getModIcons()[finalIndex] = sprite;
			});
		}
	}


	private void printAllCollections()
	{
		Executors.newSingleThreadExecutor().execute(() -> {
			java.util.List<CollectionItem> items = CollectionDatabase.getAllItems();

			if (items.isEmpty()) {
				SwingUtilities.invokeLater(() ->
						panelLog("No collection items found in database.")
				);
				return;
			}

			StringBuilder sb = new StringBuilder("📘 Collection Log Items:\n\n");
			for (CollectionItem item : items) {
				sb.append(String.format("- %s x%d\n", item.getName(), item.getCount()));
			}

			String finalText = sb.toString();
			SwingUtilities.invokeLater(() ->
					panelLog(finalText)
			);
		});
	}

	private void printCollectionForCategory(String category)
	{
		Executors.newSingleThreadExecutor().execute(() -> {
			java.util.List<CollectionItem> items = CollectionDatabase.getItemsByCategory(category);

			if (items.isEmpty()) {
				SwingUtilities.invokeLater(() ->
						panelLog("📁 No items found in collection log for: " + category)
				);
				return;
			}

			StringBuilder sb = new StringBuilder("📘 " + category.replace('_', ' ') + ":\n\n");
			for (CollectionItem item : items) {
				sb.append(String.format("- %s x%d\n", item.getName(), item.getCount()));
			}

			String finalText = sb.toString();
			SwingUtilities.invokeLater(() ->
					panelLog(finalText)
			);
		});
	}

	private void syncCollectionLog()
	{
		Executors.newSingleThreadExecutor().execute(() -> {
			log.info("🔄 Starting syncCollectionLog()...");

			log.info("🔧 Initializing database inside sync thread...");
			CollectionDatabase.init();

			CollectionDatabase.clearAll();


			// ✅ Ensure DB is initialized on the same thread before inserting
			log.info("🔧 Initializing database inside sync thread...");
			CollectionDatabase.init();

			if (client.getLocalPlayer() == null) {
				log.warn("⚠️ Local player is null — not logged in yet.");
				SwingUtilities.invokeLater(() ->
						panelLog("⚠️ Cannot sync — you're not logged in yet.")
				);
				return;
			}

			String username = client.getLocalPlayer().getName();
			log.info("👤 Detected username: {}", username);

			SwingUtilities.invokeLater(() ->
					panelLog("📡 Fetching collection log for " + username + "...")
			);

			String json = TempleApiClient.fetchLog(username);
			log.info("📥 Fetched JSON: {} characters", json != null ? json.length() : 0);

			if (json == null || json.isEmpty()) {
				log.error("❌ Empty or null response from Temple API");
				SwingUtilities.invokeLater(() ->
						panelLog("❌ Failed to fetch collection log for " + username)
				);
				return;
			}

			log.info("🧩 Parsing and storing JSON...");
			CollectionParser parser = new CollectionParser();
			parser.parseAndStore(json);
			log.info("✅ Parsing complete.");

			SwingUtilities.invokeLater(() ->
					panelLog("✅ Successfully synced collection log for " + username)
			);
		});
	}

	private static final Map<String, String> CATEGORY_ALIASES = Map.ofEntries(
			Map.entry("artio", "callisto_and_artio"),
			Map.entry("callisto", "callisto_and_artio"),
			Map.entry("spindel", "venenatis_and_spindel"),
			Map.entry("venenatis", "venenatis_and_spindel"),
			Map.entry("vetion", "vetion_and_calvarion"),
			Map.entry("calvarion", "vetion_and_calvarion"),
			Map.entry("sire", "abyssal_sire"),
			Map.entry("hydra", "alchemical_hydra"),
			Map.entry("zilyana", "commander_zilyana"),
			Map.entry("graardor", "general_graardor"),
			Map.entry("kril", "kril_tsutsaroth"),
			Map.entry("tsutsaroth", "kril_tsutsaroth"),
			Map.entry("arma", "kree_arra"),
			Map.entry("kree", "kree_arra"),
			Map.entry("muspah", "phantom_muspah"),
			Map.entry("toa", "tombs_of_amascut"),
			Map.entry("tob", "theatre_of_blood"),
			Map.entry("cox", "chambers_of_xeric"),
			Map.entry("gauntlet", "the_gauntlet"),
			Map.entry("fightcaves", "the_fight_caves"),
			Map.entry("inferno", "the_inferno"),
			Map.entry("whisperer", "the_whisperer")
			// Add more as needed
	);


	@Subscribe
	public void onGameStateChanged(GameStateChanged gameStateChanged)
	{
		if (gameStateChanged.getGameState() == GameState.LOGGED_IN)
		{
			panelLog("🟢 Collection Tracker loaded. Use buttons above to sync or print.");
		}
	}


	@Subscribe
	public void onChatMessage(ChatMessage event) {
		final ChatMessageType type = event.getType();
		final String rawMessage = event.getMessage().trim();

		// Check message type and prefix
		if (type != ChatMessageType.PUBLICCHAT &&
				type != ChatMessageType.FRIENDSCHAT &&
				type != ChatMessageType.PRIVATECHAT &&
				type != ChatMessageType.CLAN_CHAT) {
			return;
		}

		if (!rawMessage.toLowerCase().startsWith("!log ")) {
			return;
		}

		String[] parts = rawMessage.substring(5).trim().split(" ", 2);
		if (parts.length == 0)
			return;

		String bossInput = parts[0].trim().replace(' ', '_');
		String bossKey = CATEGORY_ALIASES.getOrDefault(bossInput.toLowerCase(), bossInput.toLowerCase());

		String playerName;
		if (parts.length == 2) {
			playerName = parts[1].trim();
		} else {
			playerName = event.getName(); // fallback to sender
		}

		String localName = client.getLocalPlayer() != null ? client.getLocalPlayer().getName() : "";

		boolean isLocalPlayer = playerName.equalsIgnoreCase(localName);

		Executors.newSingleThreadExecutor().execute(() -> {
			List<CollectionItem> items;

			if (isLocalPlayer) {
				log.info("Fetching from local DB for {}", playerName);
				items = CollectionDatabase.getItemsByCategory(bossKey);
			} else {
				log.info("Fetching from API for {}", playerName);
				String json = TempleApiClient.fetchLogForChat(playerName);

				if (json == null || json.isEmpty()) {
					log.warn("No data fetched for user: {}", playerName);
					return;
				}

				CollectionDatabase.clearAll();
				CollectionParser parser = new CollectionParser();
				parser.parseAndStore(json);

				items = CollectionDatabase.getItemsByCategory(bossKey);
			}

			loadItemIcons(items);

			StringBuilder sb = new StringBuilder();
			sb.append("<col=373737>")
					.append(toTitleCase(bossKey.replace('_', ' ')))
					.append(":</col> ");

			if (items.isEmpty()) {
				sb.append("No data found.");
			} else {
				Map<Integer, CollectionItem> merged = new HashMap<>();
				for (CollectionItem item : items) {
					merged.compute(item.getItemId(), (id, existing) -> {
						if (existing == null) return item;
						existing.setCount(existing.getCount() + item.getCount());
						return existing;
					});
				}

				int i = 0;
				for (CollectionItem item : merged.values()) {
					Integer icon = itemIconIndexes.get(item.getItemId());
					if (icon != null) {
						sb.append("<img=").append(icon).append("> ");
					}
					sb.append("x").append(item.getCount());
					if (i++ < merged.size() - 1) sb.append(", ");
				}
			}

			SwingUtilities.invokeLater(() -> {
				event.getMessageNode().setRuneLiteFormatMessage(sb.toString());
				client.refreshChat();
			});
		});
	}


	private String toTitleCase(String input) {
		if (input == null || input.isEmpty()) return input;

		String[] words = input.toLowerCase().split(" ");
		StringBuilder titleCase = new StringBuilder();

		for (String word : words) {
			if (word.length() > 0) {
				titleCase.append(Character.toUpperCase(word.charAt(0)))
						.append(word.substring(1))
						.append(" ");
			}
		}

		return titleCase.toString().trim();
	}


	@Provides
	CollectionTrackerConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(CollectionTrackerConfig.class);
	}
}