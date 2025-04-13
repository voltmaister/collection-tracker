package com.voltmaister;

// Standard Java imports
import java.awt.*;
import java.awt.image.BufferedImage;
import java.sql.Timestamp;
import java.util.*;
import java.util.List;
import java.util.concurrent.Executors;

// Java Swing imports
import javax.inject.Inject;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

// External library imports
import com.google.gson.Gson;
import com.google.inject.Provides;
import com.voltmaister.config.CollectionTrackerConfig;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// RuneLite API imports
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
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
import com.voltmaister.data.CollectionItem;
import com.voltmaister.db.CollectionDatabase;
import com.voltmaister.parser.CollectionParser;
import com.voltmaister.utils.LoadIcon;
import com.voltmaister.utils.CategoryAliases;
import com.voltmaister.utils.PlayerNameUtils;
import com.voltmaister.utils.HelpMessageUtils;
import com.voltmaister.services.CollectionLogSyncService;



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
	@Inject private Gson gson;

	private NavigationButton navButton;
	private PluginPanel panel;

	private final Map<Integer, Integer> itemIconIndexes = new HashMap<>();

	private int itemIconStartIndex = -1;

	private final JTextArea outputArea = new JTextArea();

	private int count;

	private final BufferedImage icon = LoadIcon.loadIcon();
	private final Set<Integer> loadedItemIds = new HashSet<>();



	@Override
	protected void startUp() throws Exception {

		TempleApiClient.setGson(gson);

		log.info("Collection Tracker started!");

		CollectionDatabase.init();

		panel = new PluginPanel() {
		};
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBorder(new EmptyBorder(15, 15, 15, 15));
		panel.setBackground(new Color(43, 39, 35));

		// Title
		JLabel titleLabel = new JLabel("Collection Tracker", JLabel.CENTER);
		titleLabel.setForeground(Color.WHITE);
		titleLabel.setAlignmentX(JLabel.CENTER_ALIGNMENT);
		titleLabel.setFont(titleLabel.getFont().deriveFont(18f));
		panel.add(titleLabel);
		panel.add(Box.createVerticalStrut(10));

		// Button section
		JPanel buttonPanel = new JPanel();
		buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.Y_AXIS));
		buttonPanel.setBackground(new Color(43, 39, 35));
		buttonPanel.setAlignmentX(JPanel.CENTER_ALIGNMENT);

		JButton printButton = new JButton("📄 Print Collections");
		printButton.setAlignmentX(JButton.CENTER_ALIGNMENT);
		printButton.setMaximumSize(new Dimension(200, 30));
		printButton.setMargin(new Insets(5, 10, 5, 10));
		printButton.setFocusable(false);
		printButton.addActionListener(e -> printAllCollections());
		buttonPanel.add(printButton);
		buttonPanel.add(Box.createVerticalStrut(8));

		JButton syncButton = new JButton("🔄 Sync Collection Log");
		syncButton.setAlignmentX(JButton.CENTER_ALIGNMENT);
		syncButton.setMaximumSize(new Dimension(200, 30));
		syncButton.setMargin(new Insets(5, 10, 5, 10));
		syncButton.setFocusable(false);
		syncButton.addActionListener(e -> CollectionLogSyncService.syncCollectionLog(client, this::panelLog));
		buttonPanel.add(syncButton);
		buttonPanel.add(Box.createVerticalStrut(8));

		JButton helpButton = new JButton("❔ Show Commands");
		helpButton.setAlignmentX(JButton.CENTER_ALIGNMENT);
		helpButton.setMaximumSize(new Dimension(200, 30));
		helpButton.setMargin(new Insets(5, 10, 5, 10));
		helpButton.setFocusable(false);
		helpButton.addActionListener(e -> panelLog(HelpMessageUtils.getHelpMessage()));
		buttonPanel.add(helpButton);


		buttonPanel.add(Box.createVerticalStrut(5)); // extra spacing if needed


		panel.add(buttonPanel);
		panel.add(Box.createVerticalStrut(15));

		// Output area
		outputArea.setEditable(false);
		outputArea.setLineWrap(true);
		outputArea.setWrapStyleWord(true);
		outputArea.setBackground(new Color(30, 30, 30));
		outputArea.setForeground(Color.WHITE);
		outputArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
		outputArea.setBorder(new EmptyBorder(10, 10, 10, 10));

		JScrollPane scrollPane = new JScrollPane(outputArea);
		scrollPane.setPreferredSize(new Dimension(0, 200));
		scrollPane.setMaximumSize(new Dimension(Integer.MAX_VALUE, 300));
		scrollPane.setBorder(BorderFactory.createEmptyBorder());
		scrollPane.getVerticalScrollBar().setUnitIncrement(16);

		panel.add(scrollPane);

		// Navigation button
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
			String playerName = client.getLocalPlayer() != null
					? PlayerNameUtils.normalizePlayerName(Objects.requireNonNull(client.getLocalPlayer().getName()))
					: "";
			List<CollectionItem> items = CollectionDatabase.getAllItems(playerName);


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

	public static boolean isDataOutdated(String username, String lastChangedFromApi) {
		if (lastChangedFromApi == null) return true;

		Timestamp dbTimestamp = CollectionDatabase.getLatestTimestamp(username);
		Timestamp apiTimestamp = Timestamp.valueOf(lastChangedFromApi);

		return dbTimestamp == null || dbTimestamp.before(apiTimestamp);
	}



	private void printCollectionForCategory(String category)
	{
		Executors.newSingleThreadExecutor().execute(() -> {
			String playerName = client.getLocalPlayer().getName(); // ✅ declare properly
			List<CollectionItem> items = CollectionDatabase.getItemsByCategory(playerName.toLowerCase(), category); // ✅ pass it

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





	@Subscribe
	public void onGameStateChanged(GameStateChanged gameStateChanged)
	{
		if (gameStateChanged.getGameState() == GameState.LOGGED_IN)
		{
			panelLog("🟢 Collection Tracker loaded. Use buttons above to sync or print.");
		}
	}


	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		final ChatMessageType type = event.getType();
		final String rawMessage = event.getMessage().trim();

		// Only react to public, private, or clan chat
		if (type != ChatMessageType.PUBLICCHAT &&
				type != ChatMessageType.FRIENDSCHAT &&
				type != ChatMessageType.PRIVATECHAT &&
				type != ChatMessageType.CLAN_CHAT)
		{
			return;
		}

		// Command must start with "!log "
		if (!rawMessage.toLowerCase().startsWith("!log "))
		{
			return;
		}

		String[] parts = rawMessage.substring(5).trim().split(" ", 2);
		if (parts.length == 0)
			return;

		// Normalize boss name
		String bossInput = parts[0].trim().replace(' ', '_');
		String bossKey = CategoryAliases.CATEGORY_ALIASES.getOrDefault(bossInput.toLowerCase(), bossInput.toLowerCase());

		// Determine target player (specified or sender)
		String playerName = (parts.length == 2) ? parts[1].trim() : event.getName();
		String normalizedPlayerName = PlayerNameUtils.normalizePlayerName(playerName);  // Normalize the player name for the API call
		String localName = client.getLocalPlayer() != null ? client.getLocalPlayer().getName() : "";
		boolean isLocalPlayer = normalizedPlayerName.equalsIgnoreCase(localName);

		Executors.newSingleThreadExecutor().execute(() ->
		{
			String lastChanged = TempleApiClient.getLastChanged(normalizedPlayerName);
			Timestamp dbTimestamp = CollectionDatabase.getLatestTimestamp(normalizedPlayerName);
			Timestamp apiTimestamp = lastChanged != null ? Timestamp.valueOf(lastChanged) : null;

			log.info("🕒 [Compare] {} | DB: {} | API: {}", normalizedPlayerName, dbTimestamp, apiTimestamp);

			boolean hasLocalData = CollectionDatabase.hasPlayerData(normalizedPlayerName);
			boolean shouldUpdate = !hasLocalData || (apiTimestamp != null && (dbTimestamp == null || dbTimestamp.before(apiTimestamp)));


			if (shouldUpdate)
			{
				log.info("📭 No local data for '{}', fetching from API...", normalizedPlayerName);
				String json = TempleApiClient.fetchLogForChat(normalizedPlayerName);

				// Handle empty or failed fetch
				if (json == null || json.isEmpty())
				{
					log.warn("❌ No data fetched for user: {}", normalizedPlayerName);

					String errorMessage = "⚠️ Failed to fetch log for " + playerName + ".";  // Use original name here
					if (json != null && json.contains("Player has not synced"))
					{
						errorMessage = "⚠️ " + playerName + " has not synced their log on TempleOSRS.";  // Use original name here
					}

					final String finalMessage = errorMessage;
					SwingUtilities.invokeLater(() -> {
						chatMessageManager.queue(
								QueuedMessage.builder()
										.type(ChatMessageType.GAMEMESSAGE)
										.runeLiteFormattedMessage("<col=ff6666>" + finalMessage + "</col>")
										.build()
						);
					});
					return;
				}

				if (!isLocalPlayer)
				{
					CollectionDatabase.pruneOldPlayers(localName, config.maxCachedPlayers());
				}

				CollectionParser parser = new CollectionParser();
				parser.parseAndStore(PlayerNameUtils.normalizePlayerName(playerName), json);
			}
			else
			{
				log.info("✔️ Found cached data for '{}'", normalizedPlayerName);
			}

			// Fetch the requested category
			List<CollectionItem> items = CollectionDatabase.getItemsByCategory(normalizedPlayerName, bossKey);
			loadItemIcons(items);

			StringBuilder sb = new StringBuilder();

			// If sender's name is same as the player being queried, omit the player's name
			if (!event.getName().equalsIgnoreCase(playerName)) {
				sb.append("<col=373737>")
						.append(playerName)  // Append the original player name here
						.append("'s ");
			}

			sb.append(toTitleCase(bossKey.replace('_', ' ')))
					.append("</col>");

			if (items.isEmpty())
			{
				sb.append(" No data found.");
			}
			else
			{
				Map<Integer, CollectionItem> merged = new HashMap<>();
				for (CollectionItem item : items)
				{
					merged.compute(item.getItemId(), (id, existing) ->
					{
						if (existing == null) return item;
						existing.setCount(existing.getCount() + item.getCount());
						return existing;
					});
				}

				int i = 0;
				for (CollectionItem item : merged.values())
				{
					Integer icon = itemIconIndexes.get(item.getItemId());
					if (icon != null)
					{
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
	public CollectionTrackerConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(CollectionTrackerConfig.class);
	}

}