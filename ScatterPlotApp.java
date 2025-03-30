import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.Timer;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.ChartUtilities;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

/**
 * ScatterPlotApp implements a Swing application that reads CSV data,
 * groups it by a selected attribute, bins sleep hours to compute average stress,
 * and renders a scatter plot with trend lines.
 * It also provides export functionality (CSV or PNG image) and an animation toggle.
 */
public class ScatterPlotApp extends JFrame {

    // UI components
    private JButton btnOpenFile, btnReset, btnToggleAnimation, btnExport;
    private JComboBox<String> cbGroupBy;
    private JLabel messageLabel;
    private ChartPanel chartPanel;
    private FadePanel fadePanel; // custom panel for fade-in animation

    // Data storage
    private List<DataRow> dataRows = new ArrayList<>();
    private boolean animationsEnabled = true;
    private final double BIN_SIZE = 0.5;

    // Constructor
    public ScatterPlotApp() {
        setTitle("Sleep vs Stress Trend Analysis");
        setSize(1000, 700);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        initUI();
    }

    /**
     * Initializes the UI components.
     */
    private void initUI() {
        // Top panel for controls
        JPanel controlPanel = new JPanel();
        String[] groups = {"Grade", "Gender", "Department"};
        cbGroupBy = new JComboBox<>(groups);
        btnOpenFile = new JButton("Open CSV");
        btnReset = new JButton("Reset Chart");
        btnToggleAnimation = new JButton("Animations: ON");
        btnExport = new JButton("Export Output");

        controlPanel.add(new JLabel("Group By:"));
        controlPanel.add(cbGroupBy);
        controlPanel.add(btnOpenFile);
        controlPanel.add(btnReset);
        controlPanel.add(btnToggleAnimation);
        controlPanel.add(btnExport);
        controlPanel.setOpaque(false);

        // Message label
        messageLabel = new JLabel(" ");
        messageLabel.setForeground(Color.DARK_GRAY);

        // Set up listeners
        btnOpenFile.addActionListener(e -> openCSVFile());
        btnReset.addActionListener(e -> resetChart());
        btnToggleAnimation.addActionListener(e -> toggleAnimations());
        btnExport.addActionListener(e -> showExportDialog());
        cbGroupBy.addActionListener(e -> {
            if (!dataRows.isEmpty()) {
                renderChart((String) cbGroupBy.getSelectedItem());
            }
        });

        // FadePanel will wrap the chartPanel to provide fade-in effect.
        fadePanel = new FadePanel();
        fadePanel.setLayout(new BorderLayout());
        fadePanel.setPreferredSize(new Dimension(800, 500));
        fadePanel.setBackground(Color.WHITE);

        // Create an empty chart panel initially.
        chartPanel = new ChartPanel(null);
        fadePanel.add(chartPanel, BorderLayout.CENTER);

        // Add components to the main frame
        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(controlPanel, BorderLayout.NORTH);
        getContentPane().add(fadePanel, BorderLayout.CENTER);
        getContentPane().add(messageLabel, BorderLayout.SOUTH);

        // Set a custom background for the content pane (artistic gradient)
        getContentPane().setBackground(new Color(255, 214, 51));  // Use a contrasting background if desired
    }

    /**
     * Opens a CSV file and parses its contents.
     */
    private void openCSVFile() {
        JFileChooser chooser = new JFileChooser();
        int ret = chooser.showOpenDialog(this);
        if (ret == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            parseCSV(file);
        }
    }

    /**
     * Parses the CSV file using simple String splitting.
     */
    private void parseCSV(File file) {
        dataRows.clear();
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String headerLine = br.readLine();
            if (headerLine == null) {
                showMessage("CSV file is empty.", true);
                return;
            }
            // Expected headers: Sleep_Hours_per_Night,Stress_Level (1-10),Grade,Gender,Department
            String[] headers = headerLine.split(",");
            Map<String, Integer> colIndex = new HashMap<>();
            for (int i = 0; i < headers.length; i++) {
                colIndex.put(headers[i].trim(), i);
            }
            // Check for required columns
            String[] required = {"Sleep_Hours_per_Night", "Stress_Level (1-10)", "Grade", "Gender", "Department"};
            for (String col : required) {
                if (!colIndex.containsKey(col)) {
                    showMessage("CSV missing column: " + col, true);
                    return;
                }
            }
            String line;
            while ((line = br.readLine()) != null) {
                String[] tokens = line.split(",");
                try {
                    double sleep = Double.parseDouble(tokens[colIndex.get("Sleep_Hours_per_Night")].trim());
                    double stress = Double.parseDouble(tokens[colIndex.get("Stress_Level (1-10)")].trim());
                    String grade = tokens[colIndex.get("Grade")].trim();
                    String gender = tokens[colIndex.get("Gender")].trim();
                    String dept = tokens[colIndex.get("Department")].trim();
                    dataRows.add(new DataRow(sleep, stress, grade, gender, dept));
                } catch (Exception ex) {
                    // Skip invalid rows
                    System.err.println("Skipping invalid row: " + line);
                }
            }
            showMessage("CSV parsed successfully.", false);
            renderChart((String) cbGroupBy.getSelectedItem());
        } catch (Exception ex) {
            ex.printStackTrace();
            showMessage("Error reading CSV: " + ex.getMessage(), true);
        }
    }

    /**
     * Renders the scatter chart based on the selected grouping attribute.
     */
    private void renderChart(String groupBy) {
        // Group dataRows by the selected attribute.
        Map<String, List<DataRow>> groups = new HashMap<>();
        for (DataRow row : dataRows) {
            String key;
            if (groupBy.equals("Grade")) {
                key = row.getGrade();
            } else if (groupBy.equals("Gender")) {
                key = row.getGender();
            } else {
                key = row.getDepartment();
            }
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(row);
        }

        XYSeriesCollection dataset = new XYSeriesCollection();
        // For each group, compute binned averages.
        for (String key : groups.keySet()) {
            List<DataRow> groupData = groups.get(key);
            XYSeries series = new XYSeries(key);
            Map<Integer, Bin> bins = new HashMap<>();
            for (DataRow row : groupData) {
                int binIndex = (int) Math.floor(row.getSleepHours() / BIN_SIZE);
                bins.computeIfAbsent(binIndex, k -> new Bin()).add(row);
            }
            List<Bin> sortedBins = new ArrayList<>(bins.values());
            sortedBins.sort((b1, b2) -> Double.compare(b1.getAverageSleep(), b2.getAverageSleep()));
            for (Bin bin : sortedBins) {
                series.add(bin.getAverageSleep(), bin.getAverageStress());
            }
            dataset.addSeries(series);
        }

        // Create scatter chart with trend lines
        JFreeChart chart = ChartFactory.createScatterPlot(
                "Average Stress per Sleep Hour Trend",
                "Average Sleep Hours",
                "Average Stress (1-10)",
                dataset
        );

        // Customize plot appearance
        XYPlot plot = chart.getXYPlot();
        plot.setBackgroundPaint(new Color(255, 255, 255));
        plot.setDomainGridlinePaint(new Color(200, 200, 200));
        plot.setRangeGridlinePaint(new Color(200, 200, 200));
        // Draw lines between points
        XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer(true, true);
        int seriesCount = dataset.getSeriesCount();
        for (int i = 0; i < seriesCount; i++) {
            // Use a simple color mapping based on group key (you can refine this)
            renderer.setSeriesPaint(i, getColor(groupBy, dataset.getSeriesKey(i).toString()));
            renderer.setSeriesShapesVisible(i, true);
            renderer.setSeriesLinesVisible(i, true);
        }
        plot.setRenderer(renderer);

        // Create a new ChartPanel and add it to the fadePanel
        chartPanel = new ChartPanel(chart);
        chartPanel.setPreferredSize(new Dimension(800, 500));
        fadePanel.removeAll();
        fadePanel.add(chartPanel, BorderLayout.CENTER);
        fadePanel.revalidate();
        fadePanel.repaint();

        // Trigger fade-in animation if enabled
        if (animationsEnabled) {
            fadePanel.fadeIn();
        } else {
            fadePanel.setAlpha(1f);
        }
    }

    /**
     * Resets the chart and clears the loaded data.
     */
    private void resetChart() {
        dataRows.clear();
        fadePanel.removeAll();
        fadePanel.repaint();
        showMessage("Chart has been reset.", false);
    }

    /**
     * Toggles the animation state and updates the chart panel accordingly.
     */
    private void toggleAnimations() {
        animationsEnabled = !animationsEnabled;
        btnToggleAnimationUpdate();
        showMessage("Animations " + (animationsEnabled ? "enabled" : "disabled") + ".", false);
    }

    private void btnToggleAnimationUpdate() {
        if (animationsEnabled) {
            btnToggleAnimation.setText("Animations: ON");
            btnToggleAnimation.setBackground(new Color(39, 174, 96)); // green
        } else {
            btnToggleAnimation.setText("Animations: OFF");
            btnToggleAnimation.setBackground(new Color(231, 76, 60)); // red
        }
    }

    /**
     * Displays the export options dialog.
     */
    private void showExportDialog() {
        String[] options = {"Export as CSV", "Export as PNG Image"};
        int choice = JOptionPane.showOptionDialog(this,
                "Choose export format:",
                "Export Output",
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.INFORMATION_MESSAGE,
                null,
                options,
                options[0]);
        if (choice == 0) {
            exportCSV();
        } else if (choice == 1) {
            exportChartAsImage();
        }
    }

    /**
     * Exports the computed chart data as a human-readable CSV file.
     */
    private void exportCSV() {
        if (dataRows.isEmpty()) {
            showMessage("No data available for export.", true);
            return;
        }
        String groupBy = (String) cbGroupBy.getSelectedItem();
        // Build CSV content with padding for alignment
        StringBuilder sb = new StringBuilder();
        sb.append("=== Chart Export ===\n\n");
        Map<String, List<Bin>> exportData = new HashMap<>();
        // Recreate grouping and binning
        Map<String, List<DataRow>> groups = new HashMap<>();
        for (DataRow row : dataRows) {
            String key;
            if (groupBy.equals("Grade")) key = row.getGrade();
            else if (groupBy.equals("Gender")) key = row.getGender();
            else key = row.getDepartment();
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(row);
        }
        for (String key : groups.keySet()) {
            List<DataRow> groupData = groups.get(key);
            Map<Integer, Bin> bins = new HashMap<>();
            for (DataRow row : groupData) {
                int binIndex = (int) Math.floor(row.getSleepHours() / BIN_SIZE);
                bins.computeIfAbsent(binIndex, k -> new Bin()).add(row);
            }
            List<Bin> sortedBins = new ArrayList<>(bins.values());
            sortedBins.sort((b1, b2) -> Double.compare(b1.getAverageSleep(), b2.getAverageSleep()));
            exportData.put(key, sortedBins);
        }
        // Write CSV lines per group
        for (String key : exportData.keySet()) {
            sb.append("Dataset: ").append(key).append("\n");
            String headerLine = String.format("%-12s,%-12s\n", "Avg Sleep", "Avg Stress");
            sb.append(headerLine);
            for (Bin bin : exportData.get(key)) {
                String line = String.format("%-12.2f,%-12.2f\n", bin.getAverageSleep(), bin.getAverageStress());
                sb.append(line);
            }
            sb.append("\n");
        }
        // Save CSV file using JFileChooser for output file
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Save Exported CSV");
        int userSelection = chooser.showSaveDialog(this);
        if (userSelection == JFileChooser.APPROVE_OPTION) {
            File fileToSave = chooser.getSelectedFile();
            try (BufferedWriter bw = new BufferedWriter(new FileWriter(fileToSave))) {
                bw.write(sb.toString());
                showMessage("CSV exported successfully.", false);
            } catch (Exception ex) {
                ex.printStackTrace();
                showMessage("Error exporting CSV: " + ex.getMessage(), true);
            }
        }
    }

    /**
     * Exports the chart as a PNG image file.
     */
    private void exportChartAsImage() {
        if (chartPanel == null) {
            showMessage("No chart available for export.", true);
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Save Chart as PNG");
        int userSelection = chooser.showSaveDialog(this);
        if (userSelection == JFileChooser.APPROVE_OPTION) {
            File fileToSave = chooser.getSelectedFile();
            try {
                ChartUtilities.saveChartAsPNG(fileToSave, chartPanel.getChart(), chartPanel.getWidth(), chartPanel.getHeight());
                showMessage("Chart exported as image successfully.", false);
            } catch (Exception ex) {
                ex.printStackTrace();
                showMessage("Error exporting chart image: " + ex.getMessage(), true);
            }
        }
    }

    /**
     * Displays a message in the message label.
     * @param msg The message.
     * @param isError true if error (red), false if info (green).
     */
    private void showMessage(String msg, boolean isError) {
        messageLabel.setText(msg);
        messageLabel.setForeground(isError ? Color.RED : new Color(39, 174, 96));
    }

    /**
     * Custom fade panel that supports fade-in animation.
     */
    private class FadePanel extends JPanel {
        private float alpha = 1f; // opaque

        public void setAlpha(float a) {
            this.alpha = a;
            repaint();
        }

        public float getAlpha() {
            return this.alpha;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2d = (Graphics2D) g.create();
            g2d.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, alpha));
            super.paintComponent(g2d);
            g2d.dispose();
        }

        /**
         * Fades in the panel using a Timer.
         */
        public void fadeIn() {
            setAlpha(0f);
            Timer timer = new Timer(50, null);
            timer.addActionListener(new ActionListener() {
                float value = 0f;
                public void actionPerformed(ActionEvent e) {
                    value += 0.1f;
                    if (value >= 1f) {
                        value = 1f;
                        timer.stop();
                    }
                    setAlpha(value);
                }
            });
            timer.start();
        }
    }

    /**
     * Represents a row of CSV data.
     */
    private class DataRow {
        private double sleepHours;
        private double stress;
        private String grade;
        private String gender;
        private String department;

        public DataRow(double sleepHours, double stress, String grade, String gender, String department) {
            this.sleepHours = sleepHours;
            this.stress = stress;
            this.grade = grade;
            this.gender = gender;
            this.department = department;
        }

        public double getSleepHours() {
            return sleepHours;
        }

        public double getStress() {
            return stress;
        }

        public String getGrade() {
            return grade;
        }

        public String getGender() {
            return gender;
        }

        public String getDepartment() {
            return department;
        }
    }

    /**
     * Helper class for binning data.
     */
    private class Bin {
        private double totalSleep = 0;
        private double totalStress = 0;
        private int count = 0;

        public void add(DataRow row) {
            totalSleep += row.getSleepHours();
            totalStress += row.getStress();
            count++;
        }

        public double getAverageSleep() {
            return count > 0 ? totalSleep / count : 0;
        }

        public double getAverageStress() {
            return count > 0 ? totalStress / count : 0;
        }
    }

    public static void main(String[] args) {
        // Launch the application on the Event Dispatch Thread
        EventQueue.invokeLater(() -> {
            ScatterPlotApp app = new ScatterPlotApp();
            app.setVisible(true);
        });
    }
}
