import java.time.LocalDate;
import java.util.ArrayList;

public class Task {
    private String name;
    private String importance;
    private LocalDate deadline;
    private java.util.List<String> tags;
    private boolean isCompleted;

    // конструктор задачи
    public Task(String name, String importance, LocalDate deadline, java.util.List<String> tags) {
        this.name = name;
        this.importance = importance;
        this.deadline = deadline;
        this.tags = new ArrayList<>(tags);
        this.isCompleted = false;
    }

    // геттеры и сеттеры для полей
    public String getName() { return name; }
    public String getImportance() { return importance; }
    public LocalDate getDeadline() { return deadline; }
    public java.util.List<String> getTags() { return tags; }
    public String getTagsAsString() { return String.join(", ", tags); }
    public boolean isCompleted() { return isCompleted; }

    public void setName(String name) { this.name = name; }
    public void setImportance(String importance) { this.importance = importance; }
    public void setDeadline(LocalDate deadline) { this.deadline = deadline; }
    public void setTags(java.util.List<String> tags) { this.tags = tags; }
    public void setCompleted(boolean completed) { this.isCompleted = completed; }
}