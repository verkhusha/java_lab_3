import java.time.LocalDate;
import java.util.*;

// ==================== ENUMS ====================

enum CardType {
    STUDENT, PUPIL, REGULAR
}

enum PeriodType {
    MONTH, TEN_DAYS
}

// ==================== MODEL ====================

abstract class TravelCard {
    protected final String id;
    protected final CardType type;

    public TravelCard(String id, CardType type) {
        this.id = id;
        this.type = type;
    }

    public String getId() { return id; }
    public CardType getType() { return type; }

    public abstract boolean isValid(LocalDate currentDate);
    public abstract boolean useTrip(LocalDate currentDate);
}

class PeriodCard extends TravelCard {
    private final LocalDate issueDate;
    private final PeriodType period;

    public PeriodCard(String id, CardType type, LocalDate issueDate, PeriodType period) {
        super(id, type);
        this.issueDate = issueDate;
        this.period = period;
    }

    public LocalDate getExpiryDate() {
        return period == PeriodType.MONTH ?
                issueDate.plusMonths(1).minusDays(1) :
                issueDate.plusDays(10);
    }

    @Override
    public boolean isValid(LocalDate currentDate) {
        return !currentDate.isAfter(getExpiryDate());
    }

    @Override
    public boolean useTrip(LocalDate currentDate) {
        return isValid(currentDate);
    }

    @Override
    public String toString() {
        return String.format("PeriodCard{id='%s', type=%s, expiry=%s}", id, type, getExpiryDate());
    }
}

class TripCountCard extends TravelCard {
    private int remainingTrips;

    public TripCountCard(String id, CardType type, int trips) {
        super(id, type);
        this.remainingTrips = trips;
    }

    public int getRemainingTrips() { return remainingTrips; }

    @Override
    public boolean isValid(LocalDate currentDate) {
        return remainingTrips > 0;
    }

    @Override
    public boolean useTrip(LocalDate currentDate) {
        if (remainingTrips <= 0) return false;
        remainingTrips--;
        return true;
    }

    @Override
    public String toString() {
        return String.format("TripCountCard{id='%s', type=%s, tripsLeft=%d}", id, type, remainingTrips);
    }
}

class AccumulativeCard extends TravelCard {
    private double balance;
    private static final double FARE = 25.0;

    public AccumulativeCard(String id, double initialBalance) {
        super(id, CardType.REGULAR);
        this.balance = initialBalance;
    }

    public double getBalance() { return balance; }
    public void topUp(double amount) { this.balance += amount; }

    @Override
    public boolean isValid(LocalDate currentDate) {
        return balance >= FARE;
    }

    @Override
    public boolean useTrip(LocalDate currentDate) {
        if (balance < FARE) return false;
        balance -= FARE;
        return true;
    }

    @Override
    public String toString() {
        return String.format("AccumulativeCard{id='%s', balance=%.2f}", id, balance);
    }
}

class CardRegistry {
    private final Map<String, TravelCard> cards = new HashMap<>();

    public void issueCard(TravelCard card) {
        cards.put(card.getId(), card);
    }

    public TravelCard getCard(String id) {
        return cards.get(id);
    }

    public Collection<TravelCard> getAllCards() {
        return cards.values();
    }
}

class TurnstileStats {
    private int totalPasses = 0;
    private int totalDenials = 0;
    private final Map<CardType, Integer> passesByType = new HashMap<>();
    private final Map<CardType, Integer> denialsByType = new HashMap<>();

    public void recordPass(CardType type) {
        totalPasses++;
        passesByType.merge(type, 1, Integer::sum);
    }

    public void recordDenial(CardType type) {
        totalDenials++;
        denialsByType.merge(type, 1, Integer::sum);
    }

    public int getTotalPasses() { return totalPasses; }
    public int getTotalDenials() { return totalDenials; }

    public Map<CardType, Integer> getPassesByType() { return new HashMap<>(passesByType); }
    public Map<CardType, Integer> getDenialsByType() { return new HashMap<>(denialsByType); }

    public String getSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Загальна статистика ===\n");
        sb.append("Дозволено: ").append(totalPasses).append("\n");
        sb.append("Відмовлено: ").append(totalDenials).append("\n\n");

        sb.append("=== Проходи за типами ===\n");
        for (CardType type : CardType.values()) {
            int count = passesByType.getOrDefault(type, 0);
            if (count > 0) {
                sb.append(type).append(": ").append(count).append("\n");
            }
        }

        sb.append("\n=== Відмови за типами ===\n");
        for (CardType type : CardType.values()) {
            int count = denialsByType.getOrDefault(type, 0);
            if (count > 0) {
                sb.append(type).append(": ").append(count).append("\n");
            }
        }

        return sb.toString();
    }
}

// ==================== VIEW ====================

interface TurnstileView {
    void showPassGranted(String cardId);
    void showPassDenied(String cardId, String reason);
    void showStatistics(String stats);
    void showError(String message);
    void showInfo(String message);  // НОВЕ: для інформаційних повідомлень
}

class ConsoleTurnstileView implements TurnstileView {
    @Override
    public void showPassGranted(String cardId) {
        System.out.println("Прохід ДОЗВОЛЕНО для картки: " + cardId);
    }

    @Override
    public void showPassDenied(String cardId, String reason) {
        System.out.println("Прохід ЗАБОРОНЕНО для картки " + cardId + ": " + reason);
    }

    @Override
    public void showStatistics(String stats) {
        System.out.println("\n" + stats);
    }

    @Override
    public void showError(String message) {
        System.err.println("ПОМИЛКА: " + message);
    }

    @Override
    public void showInfo(String message) {
        System.out.println("ІНФО: " + message);
    }
}

// ==================== CONTROLLER ====================

class TurnstileController {
    private final CardRegistry registry;
    private final TurnstileStats stats;
    private final TurnstileView view;
    private final LocalDate currentDate;

    public TurnstileController(CardRegistry registry, TurnstileStats stats, TurnstileView view) {
        this.registry = registry;
        this.stats = stats;
        this.view = view;
        this.currentDate = LocalDate.now();
    }

    public void processCard(String cardId) {
        TravelCard card = registry.getCard(cardId);
        if (card == null) {
            view.showPassDenied(cardId, "Картка не знайдена в реєстрі");
            stats.recordDenial(CardType.REGULAR);
            return;
        }

        if (!card.isValid(currentDate)) {
            String reason;
            if (card instanceof PeriodCard periodCard) {
                reason = "Термін дії закінчився (" + periodCard.getExpiryDate() + ")";
            } else if (card instanceof TripCountCard tripCard) {
                reason = "Немає поїздок (залишилось: " + tripCard.getRemainingTrips() + ")";
            } else if (card instanceof AccumulativeCard accCard) {
                reason = "Недостатньо коштів (баланс: " + String.format("%.2f", accCard.getBalance()) + ")";
            } else {
                reason = "Недійсна картка";
            }
            view.showPassDenied(cardId, reason);
            stats.recordDenial(card.getType());
            return;
        }

        boolean used = card.useTrip(currentDate);
        if (used) {
            view.showPassGranted(cardId);
            stats.recordPass(card.getType());
        } else {
            view.showPassDenied(cardId, "Не вдалося списати поїздку");
            stats.recordDenial(card.getType());
        }
    }

    public void showStatistics() {
        view.showStatistics(stats.getSummary());
    }

    public void issuePeriodCard(String id, CardType type, PeriodType period) {
        if (type == CardType.REGULAR && period == PeriodType.TEN_DAYS) {
            view.showError("Звичайні картки не можуть мати термін 10 днів");
            return;
        }
        PeriodCard card = new PeriodCard(id, type, currentDate, period);
        registry.issueCard(card);
        view.showInfo("Видано картку: " + card);
    }

    public void issueTripCard(String id, CardType type, int trips) {
        TripCountCard card = new TripCountCard(id, type, trips);
        registry.issueCard(card);
        view.showInfo("Видано картку: " + card);
    }

    public void issueAccumulativeCard(String id, double balance) {
        AccumulativeCard card = new AccumulativeCard(id, balance);
        registry.issueCard(card);
        view.showInfo("Видано накопичувальну картку: " + card);
    }
}

// ==================== MAIN ====================

public class TurnstileMVC {
    public static void main(String[] args) {
        // Налаштування кодування для коректного виводу кирилиці
        System.setProperty("file.encoding", "UTF-8");
        System.setProperty("console.encoding", "UTF-8");

        CardRegistry registry = new CardRegistry();
        TurnstileStats stats = new TurnstileStats();
        TurnstileView view = new ConsoleTurnstileView();
        TurnstileController controller = new TurnstileController(registry, stats, view);

        // === Видача карток ===
        controller.issuePeriodCard("STU001", CardType.STUDENT, PeriodType.MONTH);
        controller.issuePeriodCard("PUP001", CardType.PUPIL, PeriodType.TEN_DAYS);
        controller.issueTripCard("REG001", CardType.REGULAR, 5);
        controller.issueTripCard("STU002", CardType.STUDENT, 10);
        controller.issueAccumulativeCard("ACC001", 100.0);

        // === Симуляція ===
        System.out.println("=== Симуляція роботи турнікета ===");
        controller.processCard("STU001");
        controller.processCard("PUP001");
        controller.processCard("REG001");
        controller.processCard("REG001");
        controller.processCard("REG001");
        controller.processCard("REG001");
        controller.processCard("REG001");
        controller.processCard("REG001"); // Відмова
        controller.processCard("ACC001");
        controller.processCard("ACC001");
        controller.processCard("UNKNOWN");

        controller.showStatistics();
    }
}