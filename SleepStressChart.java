import com.opencsv.CSVReader;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import org.jfree.chart.ui.ApplicationFrame;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.FileReader;
import java.util.*;
import java.util.List;

public class SleepStressChart extends ApplicationFrame {

    private JPanel contentPane;
    private JComboBox<String> groupCombo;
    private JButton btnChooseFile;
    private JButton btnReset;
    private ChartPanel chartPanel;
    private XYSeriesCollection dataset;
    private List<Map<String, String>> rawData = new ArrayList<>();
    private final double BIN_SIZE = 0.5;

    // Constructor sets up UI
    public SleepStressChart(String title) {
        super(title);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setBounds(100, 100, 1000, 700);

        contentPane = new JPanel(new BorderLayout(10, 10));
        contentPane.setBorder(new EmptyBorder(10, 10, 10, 10));
        setContentPane(contentPane);

        // Create a hero header
        JPanel headerPanel = new JPanel();
        headerPanel.setBackground(new Color(52, 152, 219)); // similar to #3498db
        headerPanel.setLayout(new BoxLayout(headerPanel, BoxLayout.Y_AXIS));
        headerPanel.setBorder(new EmptyBorder(20, 20, 20, 20));

        JLabel lblTitle = new JLabel("Sleep vs Stress Trends");
        lblTitle.setFont(new Font("Poppins", Font.BOLD, 32));
        lblTitle.setForeground(Color.WHITE);
        lblTitle.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel lblSubtitle = new JLabel("<html>Upload a CSV with <strong>Sleep_Hours_per_Night</strong>, <strong>Stress_Level (1-10)</strong>, <strong>Grade</strong>, <strong>Gender</strong>, and <strong>Department</strong>.</html>");
        lblSubtitle.setFont(new Font("Poppins", Font.PLAIN, 18));
        lblSubtitle.setForeground(Color.WHITE);
        lblSubtitle.setAlignmentX(Component.CENTER_ALIGNMENT);

        headerPanel.add(lblTitle);
        headerPanel.add(Box.createVerticalStrut(10));
        headerPanel.add(lblSubtitle);
        contentPane.add(headerPanel, BorderLayout.NORTH);

        // Controls Panel
        JPanel controlsPanel = new JPanel();
        controlsPanel.setLayout(new FlowLayout(FlowLayout.CENTER, 15, 10));

        btnChooseFile = new JButton("Choose CSV File");
        btnChooseFile.addActionListener(e -> chooseFile());

        groupCombo = new JComboBox<>(new String[]{"Grade", "Gender", "Department"});
        groupCombo.addActionListener(e -> {
            if (!rawData.isEmpty()) {
                updateChart();
            }
        });

        btnReset = new JButton("Reset Chart");
        btnReset.addActionListener(e -> resetChart());

        controlsPanel.add(btnChooseFile);
        controlsPanel.add(new JLabel("Group By:"));
        controlsPanel.add(groupCombo);
        controlsPanel.add(btnReset);

        contentPane.add(controlsPanel, BorderLayout.SOUTH);

        // Chart Panel with empty dataset initially.
        dataset = new XYSeriesCollection();
        JFreeChart chart = createChart(dataset);
        chartPanel = new ChartPanel(chart);
        chartPanel.setMouseWheelEnabled(true);
        contentPane.add(chartPanel, BorderLayout.CENTER);
    }

    // Creates the chart with custom axes, renderer, etc.
    private JFreeChart createChart(XYSeriesCollection dataset) {
        JFreeChart chart = ChartFactory.createScatterPlot(
                "Average Stress per Sleep Hour Trend",
                "Average Sleep Hours",
                "Average Stress (1-10)",
                dataset
        );

        XYPlot plot = chart.getXYPlot();
        NumberAxis domain = (NumberAxis) plot.getDomainAxis();
        domain.setRange(0.0, 12.0);
        NumberAxis range = (NumberAxis) plot.getRangeAxis();
        range.setRange(0.0, 10.0);

        XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer();
        renderer.setBaseShapesVisible(true);
        renderer.setBaseLinesVisible(true);
        renderer.setSeriesShape(0, new Rectangle(-4, -4, 8, 8));
        plot.setRenderer(renderer);

        return chart;
    }

    // Choose file using JFileChooser
    private void chooseFile() {
        JFileChooser fileChooser = new JFileChooser();
        int ret = fileChooser.showOpenDialog(this);
        if (ret == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            readCSV(file);
        }
    }

    // Read CSV file using OpenCSV
    private void readCSV(File file) {
        rawData.clear();
        try (CSVReader reader = new CSVReader(new FileReader(file))) {
            String[] headers = reader.readNext();
            if (headers == null || !validateHeaders(headers)) {
                JOptionPane.showMessageDialog(this, "CSV file is missing required columns.", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            String[] nextLine;
            while ((nextLine = reader.readNext()) != null) {
                Map<String, String> row = new HashMap<>();
                for (int i = 0; i < headers.length; i++) {
                    row.put(headers[i], i < nextLine.length ? nextLine[i] : "");
                }
                // Validate numeric values
                try {
                    Double.parseDouble(row.get("Sleep_Hours_per_Night"));
                    Double.parseDouble(row.get("Stress_Level (1-10)"));
                } catch (NumberFormatException e) {
                    continue;
                }
                rawData.add(row);
            }
            updateChart();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Error reading CSV: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // Check if CSV has the required columns
    private boolean validateHeaders(String[] headers) {
        List<String> required = Arrays.asList("Sleep_Hours_per_Night", "Stress_Level (1-10)", "Grade", "Gender", "Department");
        return Arrays.asList(headers).containsAll(required);
    }

    // Update the chart based on the current grouping selection
    private void updateChart() {
        String groupBy = (String) groupCombo.getSelectedItem();
        XYSeriesCollection newDataset = new XYSeriesCollection();

        // Group data by selected attribute
        Map<String, List<Map<String, String>>> groups = new HashMap<>();
        for (Map<String, String> row : rawData) {
            String key = row.get(groupBy);
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(row);
        }

        // For each group, compute binned averages
        for (String groupVal : groups.keySet()) {
            List<Map<String, String>> groupData = groups.get(groupVal);
            List<Point2D> binnedPoints = computeBinnedAverages(groupData);
            if (binnedPoints.isEmpty()) continue;
            XYSeries series = new XYSeries(groupVal);
            for (Point2D p : binnedPoints) {
                series.add(p.getX(), p.getY());
            }
            newDataset.addSeries(series);
        }

        // Update chart dataset
        dataset.removeAllSeries();
        for (int i = 0; i < newDataset.getSeriesCount(); i++) {
            XYSeries series = newDataset.getSeries(i);
            dataset.addSeries(series);
        }

        // Update renderer with colors based on grouping
        XYPlot plot = chartPanel.getChart().getXYPlot();
        XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer(true, true);
        int seriesIndex = 0;
        for (String groupVal : groups.keySet()) {
            renderer.setSeriesPaint(seriesIndex, getColor((String) groupCombo.getSelectedItem(), groupVal));
            renderer.setSeriesShape(seriesIndex, new Rectangle(-4, -4, 8, 8));
            seriesIndex++;
        }
        plot.setRenderer(renderer);
    }

    // Compute binned averages per group using a bin size (returns list of Point2D objects)
    private List<Point2D> computeBinnedAverages(List<Map<String, String>> groupData) {
        Map<Integer, double[]> bins = new TreeMap<>();
        for (Map<String, String> row : groupData) {
            double sleep = Double.parseDouble(row.get("Sleep_Hours_per_Night"));
            double stress = Double.parseDouble(row.get("Stress_Level (1-10)"));
            int binIndex = (int) Math.floor(sleep / BIN_SIZE);
            double[] arr = bins.getOrDefault(binIndex, new double[]{0, 0, 0});
            arr[0] += sleep;   // sum of sleep
            arr[1] += stress;  // sum of stress
            arr[2] += 1;       // count
            bins.put(binIndex, arr);
        }
        List<Point2D> points = new ArrayList<>();
        for (Map.Entry<Integer, double[]> entry : bins.entrySet()) {
            double[] arr = entry.getValue();
            double avgSleep = arr[0] / arr[2];
            double avgStress = arr[1] / arr[2];
            points.add(new Point2D(avgSleep, avgStress));
        }
        points.sort(Comparator.comparingDouble(Point2D::getX));
        return points;
    }

    // Helper class for 2D points
    private static class Point2D {
        private final double x;
        private final double y;
        public Point2D(double x, double y) { this.x = x; this.y = y; }
        public double getX() { return x; }
        public double getY() { return y; }
    }

    // Reset Zoom using ChartPanel's built-in functionality
    private void resetZoom() {
        chartPanel.restoreAutoBounds();
    }

    // Reset chart and clear file selection
    private void resetChart() {
        if (scatterChart != null) {
            scatterChart.fireChartChanged(); // or simply recreate empty dataset
        }
        rawData.clear();
        csvFileInput.setSelectedFile(null);
        dataset.removeAllSeries();
        showMessage("Chart has been reset.", "info");
    }

    // Main method to launch the application
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            SleepStressChart frame = new SleepStressChart("Sleep vs Stress Trend Analysis");
            frame.setVisible(true);
        });
    }
}
