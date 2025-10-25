import javax.swing.*;
import javax.swing.event.*;
import javax.swing.filechooser.*;
import javax.swing.table.*;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.FlowLayout;
import java.awt.event.*;
import java.io.*;
import java.time.*;
import java.time.format.*;
import java.util.*;
import java.util.stream.*;


public class ListApp extends JFrame {
    // формат отображения даты
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private boolean hasUnsavedChanges = false;
    private File currentFile = null;

    // список задач
    private List<Task> tasks = new ArrayList<>();
    private TaskTableModel tableModel;
    private JTable taskTable;
    private TableRowSorter<TableModel> sorter;

    // для ввода добавления / редактирования задач
    private JTextField nameInput = new JTextField(20);
    private JComboBox<String> importanceInput = new JComboBox<>(new String[]{"низкая", "средняя", "высокая"});
    private JTextField deadlineInput = new JTextField(10);
    private JTextField tagsInput = new JTextField(20);

    // панель фильтров
    private JTextField nameSearchField = new JTextField(15);
    private JTextField tagSearchField = new JTextField(15);
    private JComboBox<String> importanceFilterCombo = new JComboBox<>(new String[]{"все", "низкая", "средняя", "высокая"});
    private JTextField dateFromFilter = new JTextField(10);
    private JTextField dateToFilter = new JTextField(10);
    private JCheckBox hideCompletedCheckbox = new JCheckBox("Скрыть выполненные");

    public ListApp() {
        setTitle("To-Do List Manager");
        setSize(1000, 700);
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setLocationRelativeTo(null);

        // базовый вид интерфейса
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }

        tableModel = new TaskTableModel(tasks);
        taskTable = new JTable(tableModel);
        sorter = new TableRowSorter<>(tableModel);
        taskTable.setRowSorter(sorter);

        taskTable.setDefaultRenderer(Boolean.class, taskTable.getDefaultRenderer(Boolean.class));

        taskTable.setDefaultRenderer(Object.class, new ImportanceRenderer());

        taskTable.setDefaultRenderer(Date.class, new DateRenderer());

        taskTable.getColumnModel().getColumn(0).setPreferredWidth(50);
        taskTable.getColumnModel().getColumn(0).setMaxWidth(50);
        taskTable.getColumnModel().getColumn(0).setMinWidth(50);

        JPanel filterPanel = createFilterPanel();
        add(filterPanel, BorderLayout.NORTH);

        JPanel inputPanel = new JPanel(new GridBagLayout());
        inputPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        setupInputPanel(inputPanel);

        JPanel buttonPanel = createButtonPanel();

        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.add(inputPanel, BorderLayout.CENTER);
        bottomPanel.add(buttonPanel, BorderLayout.SOUTH);

        add(new JScrollPane(taskTable), BorderLayout.CENTER);
        add(bottomPanel, BorderLayout.SOUTH);

        addWindowListener(createWindowCloseHandler());
    }
    // панель фильтрации и поиска
    private JPanel createFilterPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Фильтры и поиск"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0; gbc.gridy = 0; panel.add(new JLabel("Поиск по названию задачи:"), gbc);
        gbc.gridx = 1; gbc.gridy = 0; panel.add(nameSearchField, gbc);

        gbc.gridx = 0; gbc.gridy = 1; panel.add(new JLabel("Поиск по тегам:"), gbc);
        gbc.gridx = 1; gbc.gridy = 1; panel.add(tagSearchField, gbc);

        gbc.gridx = 2; gbc.gridy = 0; panel.add(new JLabel("Важность:"), gbc);
        gbc.gridx = 3; gbc.gridy = 0; panel.add(importanceFilterCombo, gbc);

        gbc.gridx = 2; gbc.gridy = 1; panel.add(new JLabel("Дедлайн с:"), gbc);
        gbc.gridx = 3; gbc.gridy = 1; panel.add(dateFromFilter, gbc);

        gbc.gridx = 4; gbc.gridy = 1; panel.add(new JLabel("по:"), gbc);
        gbc.gridx = 5; gbc.gridy = 1; panel.add(dateToFilter, gbc);

        gbc.gridx = 4; gbc.gridy = 0;
        gbc.gridwidth = 2;
        panel.add(hideCompletedCheckbox, gbc);
        gbc.gridwidth = 1;

        DocumentListener filterListener = new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { applyFilters(); }
            @Override public void removeUpdate(DocumentEvent e) { applyFilters(); }
            @Override public void changedUpdate(DocumentEvent e) { applyFilters(); }
        };
        nameSearchField.getDocument().addDocumentListener(filterListener);
        tagSearchField.getDocument().addDocumentListener(filterListener);
        dateFromFilter.getDocument().addDocumentListener(filterListener);
        dateToFilter.getDocument().addDocumentListener(filterListener);
        importanceFilterCombo.addActionListener(e -> applyFilters());

        hideCompletedCheckbox.addActionListener(e -> applyFilters());

        return panel;
    }

    // применение фильтров в зависимости от параметров
    private void applyFilters() {
        List<RowFilter<Object, Object>> filters = new ArrayList<>();

        String nameText = nameSearchField.getText();
        if (nameText.trim().length() > 0) {
            filters.add(RowFilter.regexFilter("(?i)" + nameText, 1));
        }

        String tagText = tagSearchField.getText();
        if (tagText.trim().length() > 0) {
            filters.add(RowFilter.regexFilter("(?i)" + tagText, 4));
        }

        String importance = (String) importanceFilterCombo.getSelectedItem();
        if (!"все".equals(importance)) {
            filters.add(RowFilter.regexFilter(importance, 2));
        }

        try {
            LocalDate from = LocalDate.parse(dateFromFilter.getText(), formatter);
            Date fromDate = Date.from(from.atStartOfDay(ZoneId.systemDefault()).toInstant());
            filters.add(RowFilter.dateFilter(RowFilter.ComparisonType.AFTER, new Date(fromDate.getTime() - 1), 3));
        } catch (DateTimeParseException ignored) {}

        try {
            LocalDate to = LocalDate.parse(dateToFilter.getText(), formatter);
            LocalDate nextDay = to.plusDays(1);
            Date toDate = Date.from(nextDay.atStartOfDay(ZoneId.systemDefault()).toInstant());
            filters.add(RowFilter.dateFilter(RowFilter.ComparisonType.BEFORE, toDate, 3));
        } catch (DateTimeParseException ignored) {}

        if (hideCompletedCheckbox.isSelected()) {
            filters.add(RowFilter.regexFilter("false", 0));
        }

        if (filters.isEmpty()) {
            sorter.setRowFilter(null);
        } else {
            sorter.setRowFilter(RowFilter.andFilter(filters));
        }
    }
    // создание панели с кнопками управления задачами
    private JPanel createButtonPanel() {
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));

        Color primaryColor = new Color(1, 25, 130);
        Color successColor = new Color(1, 130, 25);
        Color dangerColor = new Color(130, 25, 1);

        JButton openButton = new JButton("Открыть");
        styleButton(openButton, primaryColor);
        openButton.addActionListener(e -> openFile());

        JButton saveButton = new JButton("Сохранить");
        styleButton(saveButton, successColor);
        saveButton.addActionListener(e -> saveToCurrentFile());

        JButton addButton = new JButton("Добавить/Обновить");
        styleButton(addButton, primaryColor);
        addButton.addActionListener(e -> addOrUpdateTask());

        JButton deleteButton = new JButton("Удалить");
        styleButton(deleteButton, dangerColor);
        deleteButton.addActionListener(e -> deleteTask());

        JButton deleteAllButton = new JButton("Удалить все");
        styleButton(deleteAllButton, dangerColor);
        deleteAllButton.addActionListener(e -> deleteAllTasks());

        JButton exitButton = new JButton("Выход");
        styleButton(exitButton, dangerColor);
        exitButton.addActionListener(e -> dispatchEvent(new WindowEvent(this, WindowEvent.WINDOW_CLOSING)));

        buttonPanel.add(openButton);
        buttonPanel.add(saveButton);
        buttonPanel.add(addButton);
        buttonPanel.add(deleteButton);
        buttonPanel.add(deleteAllButton);
        buttonPanel.add(exitButton);
        return buttonPanel;
    }
    // цвет надписи на кнопках
    private void styleButton(JButton button, Color color) {
        button.setBackground(color);
        button.setForeground(Color.WHITE);
        button.setOpaque(true);
        button.setBorderPainted(false);
        button.setFocusPainted(false);
    }
    // проверка на пустату списка задач при надатии на кнопку "удалить все"
    private void deleteAllTasks() {
        if (tasks.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Список задач уже пуст.", "Внимание", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        // на случай случайного нажатия
        String[] options = {"Да", "Нет"}; // для рускоязычных кнопок
        int response = JOptionPane.showOptionDialog(
                this,
                "Вы уверены, что хотите удалить все задачи?",
                "Внимание",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE,
                null, options, options[1]
        );

        if (response == JOptionPane.YES_OPTION) {
            tasks.clear();
            tableModel.fireTableDataChanged();
            hasUnsavedChanges = true;
            clearInputFields();
        }
    }
    // открытие уже сохраненного файла
    private void openFile() {
        if (hasUnsavedChanges) {
            String[] options = {"Да", "Нет"}; // для рускоязычных кнопок
            int result = JOptionPane.showOptionDialog(
                    this,
                    "У вас есть несохраненные изменения. Открыть новый файл без сохранения?", // если пользователь забыл сохранить старые задачи
                    "Внимание",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.QUESTION_MESSAGE,
                    null, options, options[1]
            );
            if (result == JOptionPane.NO_OPTION) {
                return;
            }
        }
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileFilter(new FileNameExtensionFilter("Текстовые файлы ToDo (*.txt)", "txt"));
        int result = fileChooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            loadTasksFromFile(file);
        }
    }

    // сохранение файла
    private void saveToCurrentFile() {
        if (currentFile == null) { // еще нет файла
            saveAsFile();
        } else {
            saveTasksToFile(currentFile); // сохраняет в существующий
        }
    }

    // новый файл для сохраниния
    private void saveAsFile() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileFilter(new FileNameExtensionFilter("Текстовые файлы ToDo (*.txt)", "txt"));
        int result = fileChooser.showSaveDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            if (!file.getName().toLowerCase().endsWith(".txt")) {
                file = new File(file.getParentFile(), file.getName() + ".txt");
            }
            saveTasksToFile(file);
        }
    }

    // сохранение задач в файл
    private void saveTasksToFile(File file) {
        try (PrintWriter writer = new PrintWriter(new FileWriter(file))) {
            for (Task task : tasks) {
                // форматирование задач для текстового файла
                String deadlineStr = (task.getDeadline() != null) ? task.getDeadline().format(formatter) : "";
                String tagsStr = String.join(",", task.getTags());
                writer.println(String.join(";", String.valueOf(task.isCompleted()), task.getName(), task.getImportance(), deadlineStr, tagsStr));
            }
            hasUnsavedChanges = false;
            currentFile = file;
            setTitle("To-Do List Manager - " + currentFile.getName());
            JOptionPane.showMessageDialog(this, "Задачи успешно сохранены в файл:\n" + file.getName(), "Внимание", JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Ошибка при сохранении файла.", "Внимание", JOptionPane.ERROR_MESSAGE);
        }
    }

    // загружает список задач из выбранного сохраненного файла
    private void loadTasksFromFile(File file) {
        List<Task> tempTasks = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(";", 5);
                // преобразование из формата текста
                if (parts.length == 4) {
                    String name = parts[0];
                    String importance = parts[1];

                    LocalDate deadline = null;
                    if (!parts[2].isEmpty()) {
                        deadline = LocalDate.parse(parts[2], formatter);
                    }
                    ArrayList<String> tags = new ArrayList<>(Arrays.asList(parts[3].split(",")));

                    Task task = new Task(name, importance, deadline, tags);
                    task.setCompleted(false);
                    tempTasks.add(task);
                }
                else if (parts.length == 5) {
                    boolean isCompleted = Boolean.parseBoolean(parts[0]);
                    String name = parts[1];
                    String importance = parts[2];

                    LocalDate deadline = null;
                    if (!parts[3].isEmpty()) {
                        deadline = LocalDate.parse(parts[3], formatter);
                    }

                    ArrayList<String> tags = new ArrayList<>(Arrays.asList(parts[4].split(",")));

                    Task task = new Task(name, importance, deadline, tags);
                    task.setCompleted(isCompleted);
                    tempTasks.add(task);
                } else {
                    throw new IOException("Неверная структура строки в файле: " + line);
                }
            }

            tasks.clear();
            tasks.addAll(tempTasks);
            tableModel.fireTableDataChanged();

            currentFile = file;
            setTitle("To-Do List Manager - " + currentFile.getName()); // сверху в заголовке будет подисано еще название открытого файла
            hasUnsavedChanges = false;
        // для попытки открыть файл не соответсвующего формата
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                    "Не удалось прочитать файл.\nУбедитесь, что файл имеет правильный формат.",
                    "Внимание",
                    JOptionPane.ERROR_MESSAGE);
        }
    }
    //панель ввода задач
    private void setupInputPanel(JPanel panel) {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;
        // координаты для расположения
        gbc.gridx = 0; gbc.gridy = 0; panel.add(new JLabel("Название задачи:"), gbc);
        gbc.gridx = 1; gbc.gridy = 0; panel.add(nameInput, gbc);
        gbc.gridx = 0; gbc.gridy = 1; panel.add(new JLabel("Важность:"), gbc);
        gbc.gridx = 1; gbc.gridy = 1; panel.add(importanceInput, gbc);
        gbc.gridx = 2; gbc.gridy = 0; panel.add(new JLabel("Дедлайн (дд.мм.гггг):"), gbc);
        gbc.gridx = 3; gbc.gridy = 0; panel.add(deadlineInput, gbc);
        gbc.gridx = 2; gbc.gridy = 1; panel.add(new JLabel("Теги (через запятую):"), gbc);
        gbc.gridx = 3; gbc.gridy = 1; panel.add(tagsInput, gbc);

        taskTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && taskTable.getSelectedRow() != -1) {
                int modelRow = taskTable.convertRowIndexToModel(taskTable.getSelectedRow());
                populateInputsForEdit(tasks.get(modelRow));
            }
        });
    }

    // добавление или обнавление задачи (проверяет корректность и добавляет, если все хорошо, иначе выводит ошибку и подсказку)
    private void addOrUpdateTask() {
        String newName = nameInput.getText().trim();
        if (newName.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Название задачи не должно быть пустым.", "Внимание", JOptionPane.ERROR_MESSAGE);
            return;
        }

        LocalDate deadline = null;
        String deadlineText = deadlineInput.getText().trim();
        if (!deadlineText.isEmpty()) {
            try {
                deadline = LocalDate.parse(deadlineText, formatter);
                if (deadline.isBefore(LocalDate.now())) {
                    JOptionPane.showMessageDialog(this, "Дата дедлайна не может быть в прошлом.", "Внимание", JOptionPane.ERROR_MESSAGE);
                    return;
                }
            } catch (DateTimeParseException e) {
                JOptionPane.showMessageDialog(this, "Неверный формат даты! Используйте дд.мм.гггг.", "Внимание", JOptionPane.ERROR_MESSAGE);
                return;
            }
        }

        int selectedRow = taskTable.getSelectedRow();
        int modelRow = (selectedRow != -1) ? taskTable.convertRowIndexToModel(selectedRow) : -1;

        String[] tagsArray = tagsInput.getText().split(",");
        ArrayList<String> tags = new ArrayList<>(Arrays.stream(tagsArray)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList()));

        // обновление данных при исправлении задачи
        if (modelRow != -1) {
            Task task = tasks.get(modelRow);
            task.setName(newName);
            task.setImportance((String) importanceInput.getSelectedItem());
            task.setDeadline(deadline);
            task.setTags(tags);
        } else {
            if (isTaskNameExists(newName)) {
                // предупреждение о создании задачи уже существующей
                String[] options = {"Да", "Нет"};
                int response = JOptionPane.showOptionDialog(
                        this,
                        "Такая задача уже создана. Вы уверены, что хотите создать снова?",
                        "Внимание",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.QUESTION_MESSAGE,
                        null, options, options[1]
                );
                if (response == JOptionPane.NO_OPTION) { return; }
            }
            Task task = new Task(newName, (String) importanceInput.getSelectedItem(), deadline, tags);
            tasks.add(task);
        }

        tableModel.fireTableDataChanged();
        applyFilters();
        hasUnsavedChanges = true;
        clearInputFields();
    }
    // сравнение имен, чтобы не было одинаковых задач
    private boolean isTaskNameExists(String name) {
        for (Task task : tasks) {
            if (task.getName().equalsIgnoreCase(name)) { return true; }
        }
        return false;
    }
    // удаление задачи
    private void deleteTask() {
        int selectedRow = taskTable.getSelectedRow();
        if (selectedRow != -1) {
            int modelRow = taskTable.convertRowIndexToModel(selectedRow);
            tasks.remove(modelRow);
            tableModel.fireTableDataChanged();
            hasUnsavedChanges = true;
            clearInputFields();
        } else {
            JOptionPane.showMessageDialog(this, "Выберите задачу для удаления.", "Внимание", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    // создаёт обработчик закрытия окна с предупреждением о несохранённых изменениях
    private WindowAdapter createWindowCloseHandler() {
        return new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (hasUnsavedChanges) {
                    String[] options = {"Да", "Нет"};
                    int response = JOptionPane.showOptionDialog(
                            ListApp.this,
                            "У вас есть несохраненные изменения. Вы уверены, что хотите выйти?",
                            "Внимание",
                            JOptionPane.YES_NO_OPTION,
                            JOptionPane.QUESTION_MESSAGE,
                            null, options, options[1]
                    );
                    if (response == JOptionPane.YES_OPTION) { System.exit(0); }
                } else {
                    System.exit(0);
                }
            }
        };
    }

    // очишяет поля ввода
    private void clearInputFields() {
        nameInput.setText("");
        importanceInput.setSelectedIndex(0);
        deadlineInput.setText("");
        tagsInput.setText("");
        taskTable.clearSelection();
    }
    // заполняет поля ввода при выборе задачи для редактирования
    private void populateInputsForEdit(Task task) {
        nameInput.setText(task.getName());
        importanceInput.setSelectedItem(task.getImportance());
        if (task.getDeadline() != null) {
            deadlineInput.setText(task.getDeadline().format(formatter));
        } else {
            deadlineInput.setText("");
        }
        tagsInput.setText(task.getTagsAsString());
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            ListApp app = new ListApp();
            app.setVisible(true);
        });
    }
}
// настраивает цвет ячеек таблицы в зависимости от важности задачи и ее статуса выполнения
class ImportanceRenderer extends DefaultTableCellRenderer {

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
        Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

        final int NAME_COLUMN_MODEL_INDEX = 1;
        final int IMPORTANCE_COLUMN_MODEL_INDEX = 2;

        if (isSelected) {
            c.setBackground(table.getSelectionBackground());
            c.setForeground(table.getSelectionForeground());
            return c;
        }

        if (column != NAME_COLUMN_MODEL_INDEX) {
            c.setBackground(table.getBackground());
            c.setForeground(table.getForeground());
            return c;
        }

        int modelRow = table.convertRowIndexToModel(row);

        Boolean isCompleted = (Boolean) table.getModel().getValueAt(modelRow, 0);

        if (isCompleted) {
            c.setBackground(new Color(200, 200, 200));
        } else {
            String importance = (String) table.getModel().getValueAt(modelRow, IMPORTANCE_COLUMN_MODEL_INDEX);

            if ("высокая".equals(importance)) {
                c.setBackground(new Color(255, 205, 205));
            } else if ("средняя".equals(importance)) {
                c.setBackground(new Color(255, 255, 205));
            } else if ("низкая".equals(importance)) {
                c.setBackground(new Color(205, 255, 205));
            } else {
                c.setBackground(table.getBackground());
            }
        }

        c.setForeground(table.getForeground());
        return c;
    }
}

// отображает дату в ячейках таблицы в формате дд.мм.гггг
class DateRenderer extends DefaultTableCellRenderer {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
        if (value instanceof Date) {
            LocalDate localDate = ((Date) value).toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
            value = localDate.format(FORMATTER);
        } else if (value == null) {
            value = "";
        }
        return super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
    }
}
// колонки для отображени списка задач
class TaskTableModel extends AbstractTableModel {
    private List<Task> tasks;
    private final String[] columnNames = {"Готово", "Название задачи", "Важность", "Дедлайн", "Теги"};

    public TaskTableModel(List<Task> tasks) {
        this.tasks = tasks;
    }

    // определяет тип данных для каждой колонки
    @Override
    public Class<?> getColumnClass(int columnIndex) {
        if (columnIndex == 0) {
            return Boolean.class;
        }
        if (columnIndex == 3) {
            return Date.class;
        }
        return String.class;
    }

    // разрешает редактирование только колонки "готово"
    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
        return columnIndex == 0;
    }

    // обновляет статус выполнения задачи
    @Override
    public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
        if (columnIndex == 0 && aValue instanceof Boolean) {
            tasks.get(rowIndex).setCompleted((Boolean) aValue);
            fireTableCellUpdated(rowIndex, columnIndex);
            fireTableRowsUpdated(rowIndex, rowIndex);
        }
    }

    @Override public int getRowCount() { return tasks.size(); } // возвращает количество задач
    @Override public int getColumnCount() { return columnNames.length; } // возвращает количество колонок
    @Override public String getColumnName(int column) { return columnNames[column]; } // возвращает название по индексу

    // возвращает значение ячейки в зависимости от колонки
    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        Task task = tasks.get(rowIndex);
        switch (columnIndex) {
            case 0: return task.isCompleted();
            case 1: return task.getName();
            case 2: return task.getImportance();
            case 3:
                if (task.getDeadline() != null) {
                    return Date.from(task.getDeadline().atStartOfDay(ZoneId.systemDefault()).toInstant());
                } else {
                    return null;
                }
            case 4: return task.getTagsAsString();
            default: return null;
        }
    }
}