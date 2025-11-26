import javafx.application.Application;
import javafx.geometry.*;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.scene.text.*;
import javafx.collections.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.paint.Color;

import java.sql.*;
import java.time.LocalDate;
import java.time.YearMonth;

import java.time.format.DateTimeFormatter;
import java.util.Optional;

public class ExpenseTrackerAllInOne extends Application {
    // --- DB Connection and User State ---
    Connection con;
    String currentUser = null;
    double currentBalance = 0;
    double pendingBalance = 0;
    int points = 0;
    int level = 1;
    int incomeCount = 0; // Tracks transaction frequency progress

    // Gamification Flags
    boolean isDarkModeEnabled = false;
    boolean isLevel10BonusActive = false;

    // --- UI Elements ---
    TableView<Transaction> table = new TableView<>();
    Label balanceLabel = new Label();
    Label pendingLabel = new Label();
    Label pointsLabel = new Label();
    Label levelLabel = new Label();
    // New Monthly Summary Labels
    Label monthlyIncomeLabel = new Label();
    Label monthlyExpenseLabel = new Label();

    // Dark Mode Toggle (Global)
    CheckBox darkModeToggle = new CheckBox("Dark Mode");
    Scene mainScene;

    // --- Start Method ---
    @Override
    public void start(Stage stage) {
        connectDB();
        stage.setTitle("💰 Gamified Financial Tracker");
        stage.setScene(loginScene(stage));
        stage.show();
    }

    // ---------------- LOGIN SCENE ----------------
    Scene loginScene(Stage stage) {
        Label title = new Label("Welcome Back!");
        title.getStyleClass().add("title");

        TextField username = new TextField();
        username.setPromptText("Username");
        PasswordField password = new PasswordField();
        password.setPromptText("Password");

        Button loginBtn = new Button("Login");
        loginBtn.getStyleClass().add("primary-button");

        Button registerBtn = new Button("New User? Register");
        registerBtn.getStyleClass().add("secondary-button");

        VBox box = new VBox(20, title, username, password, loginBtn, registerBtn);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(40));
        box.getStyleClass().add("login-pane");

        Scene scene = new Scene(box, 400, 450);
        applyStyles(scene, false);

        loginBtn.setOnAction(e -> {
            try {
                PreparedStatement ps = con.prepareStatement("SELECT * FROM users WHERE username=? AND password=?");
                ps.setString(1, username.getText());
                ps.setString(2, password.getText());
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    currentUser = rs.getString("username");
                    currentBalance = rs.getDouble("balance");
                    pendingBalance = rs.getDouble("pending");
                    points = rs.getInt("points");
                    level = rs.getInt("level");
                    incomeCount = rs.getInt("income_count");
                    isDarkModeEnabled = rs.getBoolean("dark_mode");
                    isLevel10BonusActive = (level >= 10);

                    stage.setScene(mainScene(stage));
                } else {
                    showAlert("Login Failed", "Invalid username or password.");
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });

        registerBtn.setOnAction(e -> stage.setScene(termsScene(stage)));

        return scene;
    }

    // ---------------- TERMS SCENE ----------------
    Scene termsScene(Stage stage) {
        Label title = new Label("Game Rules & Terms");
        title.getStyleClass().add("title");

        TextArea terms = new TextArea("""
                Welcome to the Gamified Expense Tracker!
                
                💰 **Money Management Rules:**
                1. Debt is tracked in your **Pending Balance**. Income first clears this debt.
                
                🌟 **Gamification Rules (Levels - Consistent Hybrid):**
                1. **Points Purpose:** Points are earned for high-value deposits and are **required** to level up. Excess points carry over!
                   - Deposits < ₹1,000: **+20 points**
                   - Deposits $\\ge$ ₹50,000: **+100 points**
                2. **Level Up Condition (HYBRID):** Leveling up requires meeting **BOTH** criteria:
                   - **Frequency:** 10 income transactions (or 8 after Level 10).
                   - **Value:** Accumulate (Level $\\times$ 100) points.
                3. **Acceleration:** Deposits $\\ge$ ₹10,000 and $\\ge$ ₹50,000 accelerate transaction progress.
                4. **Level Up Reward:** Earn a motivational alert and unlock features (L5, L10).
                
                Play smart, level up your financial discipline!
                """);
        terms.setEditable(false);
        terms.setPrefRowCount(15);
        terms.getStyleClass().add("terms-text");

        CheckBox agree = new CheckBox("I accept the terms and conditions.");
        agree.getStyleClass().add("checkbox");
        Button cont = new Button("Continue to Registration");
        cont.getStyleClass().add("primary-button");

        VBox box = new VBox(15, title, terms, agree, cont);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(20));

        Scene scene = new Scene(box, 550, 600);
        applyStyles(scene, false);

        cont.setOnAction(e -> {
            if (agree.isSelected()) stage.setScene(registerScene(stage));
            else showAlert("Notice", "You must accept the terms first!");
        });
        return scene;
    }

    // ---------------- REGISTER SCENE ----------------
    Scene registerScene(Stage stage) {
        Label title = new Label("Create Your Account");
        title.getStyleClass().add("title");

        TextField username = new TextField();
        username.setPromptText("Create Username");
        PasswordField password = new PasswordField();
        password.setPromptText("Create Password");

        TextField initBal = new TextField("0.00");
        initBal.setPromptText("Initial Balance (e.g., 500.00)");

        Button registerBtn = new Button("Finalize Registration");
        registerBtn.getStyleClass().add("primary-button");

        Button backBtn = new Button("Back to Login");
        backBtn.getStyleClass().add("secondary-button");

        VBox box = new VBox(15, title, username, password, initBal, registerBtn, backBtn);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(40));
        box.getStyleClass().add("register-pane");

        Scene scene = new Scene(box, 400, 500);
        applyStyles(scene, false);

        registerBtn.setOnAction(e -> {
            try {
                double initialBalance = Double.parseDouble(initBal.getText());
                if (initialBalance < 0) throw new IllegalArgumentException("Initial balance cannot be negative.");

                PreparedStatement ps = con.prepareStatement("INSERT INTO users VALUES(?,?,?,?,?,?,?,?)");
                ps.setString(1, username.getText());
                ps.setString(2, password.getText());
                ps.setDouble(3, initialBalance);
                ps.setDouble(4, 0); // pending
                ps.setInt(5, 0); // points
                ps.setInt(6, 1); // level
                ps.setInt(7, 0); // income_count
                ps.setBoolean(8, false); // dark_mode (default off)
                ps.executeUpdate();

                showAlert("Success", "Account created! Please login.");
                stage.setScene(loginScene(stage));
            } catch (NumberFormatException nfe) {
                showAlert("Error", "Invalid number for initial balance.");
            } catch (IllegalArgumentException iae) {
                showAlert("Error", iae.getMessage());
            } catch (SQLException sqle) {
                showAlert("Error", "Username already exists or database error. Please choose another.");
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });

        backBtn.setOnAction(e -> stage.setScene(loginScene(stage)));

        return scene;
    }

    // ---------------- MAIN SCENE ----------------
    Scene mainScene(Stage stage) {
        updateLabels();
        loadMonthlySummary(); // Load monthly summary on main scene load

        // --- Stats Panel (Top) ---
        HBox statsBox = new HBox(30, balanceLabel, pendingLabel, pointsLabel, levelLabel);
        statsBox.setAlignment(Pos.CENTER);
        statsBox.getStyleClass().add("stats-box");

        // --- Monthly Summary Panel ---
        HBox monthlySummaryBox = new HBox(20, monthlyIncomeLabel, monthlyExpenseLabel);
        monthlySummaryBox.setAlignment(Pos.CENTER);
        monthlySummaryBox.setPadding(new Insets(10, 0, 10, 0));
        monthlySummaryBox.getStyleClass().add("summary-box");

        // --- Action Panel (Middle) ---
        TextField amountField = new TextField();
        amountField.setPromptText("Enter amount (e.g., 150.50)");
        amountField.getStyleClass().add("amount-field");

        // New Category ComboBox
        ComboBox<String> categoryCombo = new ComboBox<>(FXCollections.observableArrayList("Salary", "Investment", "Food", "Bills", "Shopping", "Entertainment", "Other"));
        categoryCombo.setPromptText("Select Category");
        categoryCombo.getStyleClass().add("category-combo");

        Button addIncome = new Button("➕ Record Income");
        addIncome.getStyleClass().add("income-button");

        Button addExpense = new Button("➖ Record Expense");
        addExpense.getStyleClass().add("expense-button");

        HBox actions = new HBox(10, amountField, categoryCombo, addIncome, addExpense);
        actions.setAlignment(Pos.CENTER);
        actions.setPadding(new Insets(10, 0, 10, 0));

        // --- Controls Panel (Bottom) ---
        darkModeToggle.setSelected(isDarkModeEnabled);
        darkModeToggle.setDisable(level < 5);
        darkModeToggle.setText(level >= 5 ? "Dark Mode (Unlocked)" : "Dark Mode (Level 5 Reward)");

        darkModeToggle.setOnAction(e -> {
            isDarkModeEnabled = darkModeToggle.isSelected();
            applyStyles(mainScene, isDarkModeEnabled);
            saveUser();
        });

        Button logout = new Button("Logout");
        logout.getStyleClass().add("logout-button");
        logout.setOnAction(e -> {
            currentUser = null;
            stage.setScene(loginScene(stage));
        });

        HBox controls = new HBox(50, darkModeToggle, logout);
        controls.setAlignment(Pos.CENTER);


        // --- Transaction Table ---
        table.getColumns().clear();
        table.setPlaceholder(new Label("No transactions recorded yet."));

        TableColumn<Transaction, LocalDate> dateCol = new TableColumn<>("Date");
        dateCol.setCellValueFactory(new PropertyValueFactory<>("date"));
        dateCol.setPrefWidth(100);

        TableColumn<Transaction, String> typeCol = new TableColumn<>("Type");
        typeCol.setCellValueFactory(new PropertyValueFactory<>("type"));
        typeCol.setPrefWidth(80);

        // New Category Column
        TableColumn<Transaction, String> categoryCol = new TableColumn<>("Category");
        categoryCol.setCellValueFactory(new PropertyValueFactory<>("category"));
        categoryCol.setPrefWidth(100);


        TableColumn<Transaction, Double> amtCol = new TableColumn<>("Amount (₹)");
        amtCol.setCellValueFactory(new PropertyValueFactory<>("amount"));
        amtCol.setPrefWidth(120);

        TableColumn<Transaction, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(new PropertyValueFactory<>("status"));
        statusCol.setPrefWidth(150);

        table.getColumns().addAll(dateCol, typeCol, categoryCol, amtCol, statusCol); // Added categoryCol
        loadTransactions();

        // --- Layout ---
        VBox layout = new VBox(15, statsBox, monthlySummaryBox, actions, table, controls);
        layout.setPadding(new Insets(20));
        layout.setAlignment(Pos.TOP_CENTER);
        layout.getStyleClass().add("main-pane");

        // --- Scene & Logic ---
        mainScene = new Scene(layout, 750, 650); // Increased height slightly
        applyStyles(mainScene, isDarkModeEnabled);

        addIncome.setOnAction(e -> {
            String category = categoryCombo.getValue();
            if (category == null || category.isEmpty()) {
                showAlert("Error", "Please select a category for the transaction.");
                return;
            }
            handleIncome(amountField.getText(), category);
        });
        addExpense.setOnAction(e -> {
            String category = categoryCombo.getValue();
            if (category == null || category.isEmpty()) {
                showAlert("Error", "Please select a category for the transaction.");
                return;
            }
            handleExpense(amountField.getText(), category);
        });

        return mainScene;
    }

    // ---------------- HANDLERS ----------------

    void handleIncome(String amountText, String category) {
        try {
            double amt = Double.parseDouble(amountText);
            if (amt <= 0) {
                showAlert("Error", "Income amount must be positive.");
                return;
            }

            double oldPendingBalance = pendingBalance;

            // 1. Pending Balance check and clear
            double amountCleared = 0;
            String status = "Normal";
            if (pendingBalance > 0) {
                amountCleared = Math.min(amt, pendingBalance);
                pendingBalance -= amountCleared;
                amt -= amountCleared;
                status = "Cleared Pending";
            }

            // 2. Add remaining to current balance
            currentBalance += amt;

            // 3. Points and Leveling progress update (Tiered speed)
            int bonus = 20; // Base points
            int levelUpProgress = 1;
            double deposit = Double.parseDouble(amountText);

            // Tiered Point and Acceleration Logic
            if (deposit >= 50000) {
                bonus = 100;
                levelUpProgress = 4;
            } else if (deposit >= 10000) {
                bonus = 50;
                levelUpProgress = 2;
            } else if (deposit >= 1000) {
                bonus = 30;
                levelUpProgress = 1;
            }
            // else { bonus remains 20, progress remains 1 }

            points += bonus;
            incomeCount += levelUpProgress;

            // 4. Alerts
            if (oldPendingBalance > 0 && pendingBalance <= 0) {
                showAlert("✅ BACK ON TRACK!", "Congratulations! You have successfully cleared your Pending Balance and are debt-free!");
            }

            // High Value Deposit Alert (for any deposit >= 1000)
            if (deposit >= 1000) {
                showAlert("🚀 Value Deposit!", String.format("You earned +%d points and advanced your level progress by %d.", bonus, levelUpProgress));
            } else {
                // Small deposit alert (only if no debt cleared/alerted)
                if (oldPendingBalance <= 0 || pendingBalance > 0) {
                    showAlert("Nice Deposit!", "You earned +20 points.");
                }
            }

            // 5. Update state and UI
            updateLevel();
            recordTransaction("Income", Double.parseDouble(amountText), status, category); // Added category
            saveUser();
            updateLabels();
            loadMonthlySummary(); // Update summary

        } catch (NumberFormatException ex) {
            showAlert("Error", "Invalid number format for income.");
        }
    }

    void handleExpense(String amountText, String category) {
        try {
            double amt = Double.parseDouble(amountText);
            if (amt <= 0) {
                showAlert("Error", "Expense amount must be positive.");
                return;
            }

            if (amt > currentBalance) {
                Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
                alert.setTitle("🚨 Overspending Alert!");
                alert.setHeaderText("You're attempting to spend ₹" + String.format("%.2f", amt - currentBalance) + " more than your current Balance.");
                alert.setContentText("Do you want to add the excess to your Pending Balance (Debt)?");

                ButtonType option1 = new ButtonType("Add to Pending Balance", ButtonBar.ButtonData.OK_DONE);
                ButtonType option2 = new ButtonType("Cancel Transaction", ButtonBar.ButtonData.CANCEL_CLOSE);
                alert.getButtonTypes().setAll(option1, option2);

                Optional<ButtonType> result = alert.showAndWait();

                if (result.isPresent() && result.get() == option1) {
                    double excess = amt - currentBalance;
                    pendingBalance += excess;
                    currentBalance = 0;
                    recordTransaction("Expense", amt, "Pending", category); // Added category
                    showAlert("Debt Recorded", "The excess ₹" + String.format("%.2f", excess) + " has been added to your Pending Balance. Clear it with income!");
                } else {
                    return; // Cancel transaction
                }
            } else {
                currentBalance -= amt;
                recordTransaction("Expense", amt, "Normal", category); // Added category
            }

            saveUser();
            updateLabels();
            loadMonthlySummary(); // Update summary

        } catch (NumberFormatException ex) {
            showAlert("Error", "Invalid number format for expense.");
        }
    }

    // ---------------- HELPERS ----------------

    void updateLabels() {
        balanceLabel.setText("BALANCE: ₹" + String.format("%.2f", currentBalance));
        pendingLabel.setText("PENDING: ₹" + String.format("%.2f", pendingBalance));
        pointsLabel.setText("POINTS: " + points + " XP");
        levelLabel.setText(String.format("LEVEL %d", level));

        balanceLabel.setTextFill(currentBalance > 0 ? Color.web("#4CAF50") : Color.web("#FF9800"));
        pendingLabel.setTextFill(pendingBalance > 0 ? Color.web("#F44336") : Color.web("#2196F3"));
    }

    // --- NEW: Monthly Summary Logic ---
    void loadMonthlySummary() {
        double totalIncome = 0;
        double totalExpense = 0;

        LocalDate today = LocalDate.now();
        YearMonth currentMonth = YearMonth.from(today);
        LocalDate firstOfMonth = currentMonth.atDay(1);
        LocalDate lastOfMonth = currentMonth.atEndOfMonth();

        String monthName = today.format(DateTimeFormatter.ofPattern("MMM yyyy"));

        try {
            PreparedStatement ps = con.prepareStatement(
                    "SELECT type, SUM(amount) FROM transactions WHERE username=? AND date BETWEEN ? AND ? GROUP BY type");
            ps.setString(1, currentUser);
            ps.setDate(2, Date.valueOf(firstOfMonth));
            ps.setDate(3, Date.valueOf(lastOfMonth));
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                String type = rs.getString(1);
                double sum = rs.getDouble(2);
                if ("Income".equals(type)) {
                    totalIncome = sum;
                } else if ("Expense".equals(type)) {
                    totalExpense = sum;
                }
            }

        } catch (Exception ex) {
            ex.printStackTrace();
        }

        monthlyIncomeLabel.setText("Monthly Income (" + monthName + "): ₹" + String.format("%.2f", totalIncome));
        monthlyExpenseLabel.setText("Monthly Expense (" + monthName + "): ₹" + String.format("%.2f", totalExpense));

        monthlyIncomeLabel.getStyleClass().add("summary-income-label");
        monthlyExpenseLabel.getStyleClass().add("summary-expense-label");
    }

    // --- LEVEL UP LOGIC WITH POINT TRANSPARENCY ---
    void updateLevel() {
        int requiredTransactions = isLevel10BonusActive ? 8 : 10;
        int requiredPoints = level * 100;

        // Level up check: Must meet BOTH transaction frequency AND points threshold
        if (incomeCount >= requiredTransactions && points >= requiredPoints) {

            // 1. Deduct costs and increase level
            incomeCount = 0;
            points -= requiredPoints;
            level++;

            // Capture deduction details for the alert
            int pointsDeducted = requiredPoints;
            int pointsRemaining = points;

            // 2. Handle Rewards

            // Level 5 Reward: Enable Dark Mode Toggle
            if (level == 5) {
                darkModeToggle.setDisable(false);
                darkModeToggle.setText("Dark Mode (UNLOCKED!)");
                showAlert("⭐ LEVEL 5 ACHIEVEMENT!", "You've unlocked Dark Mode! Use the toggle below to switch themes.");
            }

            // Level 10 Reward: Permanent Speed Up
            if (level == 10) {
                isLevel10BonusActive = true;
                showAlert("🚀 LEVEL 10 BOOST!", "Leveling is permanently sped up! You now only need 8 income transactions to advance.");
            }

            // Alert (Generic motivational message with point details)
            String rewardMsg = String.format(
                    "You advanced to Level %d! 🏆\n\n" +
                            "Points Used: %d XP (Required for Level %d)\n" +
                            "Points Remaining: %d XP (Carried forward to next level progress)",
                    level, pointsDeducted, level - 1, pointsRemaining);

            showAlert("🎉 LEVEL UP: REWARD CLAIMED! 🎉", rewardMsg);

            saveUser();
            updateLabels();

            // Check again for immediate level up using remaining points (consistent reset is done)
            if (incomeCount >= requiredTransactions && points >= level * 100) {
                updateLevel();
            }
        }
    }

    void recordTransaction(String type, double amt, String status, String category) {
        try {
            // Updated SQL to include 'category'
            PreparedStatement ps = con.prepareStatement("INSERT INTO transactions(username,date,type,amount,status,category) VALUES(?,?,?,?,?,?)");
            ps.setString(1, currentUser);
            ps.setDate(2, Date.valueOf(LocalDate.now()));
            ps.setString(3, type);
            ps.setDouble(4, amt);
            ps.setString(5, status);
            ps.setString(6, category); // New column value
            ps.executeUpdate();
            loadTransactions();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    void loadTransactions() {
        try {
            ObservableList<Transaction> data = FXCollections.observableArrayList();
            // Updated SQL to select 'category'
            PreparedStatement ps = con.prepareStatement("SELECT date,type,amount,status,category FROM transactions WHERE username=? ORDER BY id DESC");
            ps.setString(1, currentUser);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) data.add(new Transaction(rs.getDate(1).toLocalDate(), rs.getString(2), rs.getDouble(3), rs.getString(4), rs.getString(5))); // Added category
            table.setItems(data);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    void saveUser() {
        try {
            PreparedStatement ps = con.prepareStatement(
                    "UPDATE users SET balance=?, pending=?, points=?, level=?, income_count=?, dark_mode=? WHERE username=?");
            ps.setDouble(1, currentBalance);
            ps.setDouble(2, pendingBalance);
            ps.setInt(3, points);
            ps.setInt(4, level);
            ps.setInt(5, incomeCount);
            ps.setBoolean(6, isDarkModeEnabled);
            ps.setString(7, currentUser);
            ps.executeUpdate();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    void connectDB() {
        try {
            // You may need to change these credentials
            con = DriverManager.getConnection("jdbc:mysql://localhost:3306/expensedb", "root", "maha2029");
            Statement st = con.createStatement();

            st.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS users(
                            username VARCHAR(50) PRIMARY KEY,
                            password VARCHAR(50),
                            balance DOUBLE,
                            pending DOUBLE,
                            points INT,
                            level INT,
                            income_count INT,
                            dark_mode BOOLEAN
                        )
                    """);

            st.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS transactions(
                            id INT AUTO_INCREMENT PRIMARY KEY,
                            username VARCHAR(50),
                            date DATE,
                            type VARCHAR(20),
                            amount DOUBLE,
                            status VARCHAR(20),
                            category VARCHAR(50) -- NEW COLUMN
                        )
                    """);
        } catch (Exception ex) {
            showAlert("Database Error", "Could not connect to the database. Ensure MySQL is running and connection details are correct.");
            ex.printStackTrace();
            System.exit(1);
        }
    }

    void showAlert(String title, String msg) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle(title);
        a.setHeaderText(null);
        a.setContentText(msg);
        a.showAndWait();
    }

    // --- STYLES (DARK MODE FIX APPLIED) ---
    void applyStyles(Scene scene, boolean isDark) {
        String baseCSS;
        if (isDark) {
            baseCSS = ".root { -fx-background-color: #333; -fx-font-family: 'Arial'; -fx-text-fill: white; }" +
                    ".label, .checkbox { -fx-text-fill: white; }" +
                    ".title { -fx-text-fill: #4CAF50; }" +
                    ".stats-box { -fx-background-color: #444; -fx-border-color: #555; }" +
                    ".summary-box { -fx-background-color: #444; -fx-border-color: #555; }" + // New style
                    ".summary-income-label { -fx-text-fill: #90EE90; }" + // Light Green
                    ".summary-expense-label { -fx-text-fill: #FFA07A; }" + // Light Salmon
                    ".secondary-button { -fx-background-color: #555; }" +

                    ".table-view { -fx-background-color: #444; }" +
                    ".table-view .column-header-background { -fx-background-color: #2196F3; }" +
                    ".table-view .column-header .label { -fx-text-fill: white; }" +
                    ".table-row-cell { -fx-background-color: #333; }" +
                    ".table-row-cell:odd { -fx-background-color: #444; }" +
                    ".table-cell { -fx-text-fill: white; }" +

                    ".text-field, .category-combo { -fx-background-color: #555; -fx-text-fill: white; -fx-prompt-text-fill: #AAA; }";
        } else {
            baseCSS = ".root { -fx-background-color: #f0f2f5; -fx-font-family: 'Arial'; }" +
                    ".title { -fx-font-size: 24px; -fx-font-weight: bold; -fx-text-fill: #333; }" +
                    ".stats-box { -fx-background-color: white; -fx-padding: 15; -fx-border-color: #DDD; -fx-border-width: 1px; -fx-border-radius: 8px; -fx-background-radius: 8px; }" +
                    ".summary-box { -fx-background-color: #E8F5E9; -fx-padding: 10; -fx-border-color: #C8E6C9; -fx-border-width: 1px; -fx-border-radius: 5px; -fx-background-radius: 5px; }" + // New style
                    ".summary-income-label { -fx-text-fill: #388E3C; -fx-font-weight: bold; }" + // Dark Green
                    ".summary-expense-label { -fx-text-fill: #D32F2F; -fx-font-weight: bold; }" + // Dark Red
                    ".label { -fx-font-size: 16px; -fx-font-weight: 600; }" +
                    ".table-view .column-header-background { -fx-background-color: #4CAF50; }" +
                    ".table-view .column-header .label { -fx-text-fill: white; }" +
                    ".table-row-cell { -fx-background-color: white; }" +
                    ".table-cell { -fx-text-fill: #333; }" +
                    ".text-field, .category-combo { -fx-background-color: white; -fx-text-fill: #333; -fx-prompt-text-fill: #777; }";
        }

        String commonCSS =
                ".button { -fx-font-size: 14px; -fx-padding: 8 15; -fx-cursor: hand; -fx-border-radius: 5px; -fx-background-radius: 5px; }" +
                        ".primary-button { -fx-background-color: #4CAF50; -fx-text-fill: white; }" +
                        ".primary-button:hover { -fx-background-color: #388E3C; }" +
                        ".secondary-button { -fx-background-color: #E0E0E0; -fx-text-fill: #333; }" +
                        ".secondary-button:hover { -fx-background-color: #BDBDBD; }" +
                        ".income-button { -fx-background-color: #2196F3; -fx-text-fill: white; -fx-font-weight: bold; }" +
                        ".expense-button { -fx-background-color: #F44336; -fx-text-fill: white; -fx-font-weight: bold; }" +
                        ".logout-button { -fx-background-color: #757575; -fx-text-fill: white; }" +
                        ".terms-text { -fx-font-family: 'Consolas', 'Monospace'; -fx-font-size: 13px; }" +
                        ".category-combo { -fx-padding: 8 15; }";

        scene.getStylesheets().clear();
        scene.getStylesheets().add("data:text/css," + baseCSS + commonCSS);
    }

    // ---------------- DATA CLASS ----------------
    public static class Transaction {
        private final LocalDate date;
        private final String type;
        private final double amount;
        private final String status;
        private final String category; // New Field

        public Transaction(LocalDate date, String type, double amount, String status, String category) {
            this.date = date;
            this.type = type;
            this.amount = amount;
            this.status = status;
            this.category = category; // Set new field
        }

        public LocalDate getDate() { return date; }
        public String getType() { return type; }
        public double getAmount() { return amount; }
        public String getStatus() { return status; }
        public String getCategory() { return category; } // New Getter
    }

    public static void main(String[] args) {
        launch(args);
    }
}