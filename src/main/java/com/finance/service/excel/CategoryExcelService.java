package com.finance.service.excel;

import com.finance.model.entity.User;
import com.finance.util.FinanceValidation;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class CategoryExcelService {

    private final LocalExcelStorageService storageService;

    private static final List<String> DEFAULT_INCOME_CATEGORIES = Arrays.asList(
            "Salary", "Freelance", "Investments", "Gifts", "Refunds", "Other Income"
    );

    private static final List<String> DEFAULT_EXPENSE_CATEGORIES = Arrays.asList(
            "Food", "Transportation", "Shopping", "Entertainment", "Healthcare",
            "Education", "Bills", "Utilities", "Rent", "Travel", "Personal Care", "Other"
    );

    public CategoryExcelService(LocalExcelStorageService storageService) {
        this.storageService = storageService;
        try {
            initializeDefaultCategories();
        } catch (Exception e) {
            // Never fail Spring startup because Excel seed failed (locked file, etc.).
            System.err.println("Warning: default categories not seeded: " + e.getMessage());
        }
    }

    private void initializeDefaultCategories() {
        List<CategoryRecord> existing = getAllCategories();
        if (existing.isEmpty()) {
            LocalDateTime now = LocalDateTime.now();
            int sortOrder = 0;

            for (String cat : DEFAULT_INCOME_CATEGORIES) {
                createCategoryInternal("INCOME", cat, "", "", now, sortOrder++);
            }
            for (String cat : DEFAULT_EXPENSE_CATEGORIES) {
                createCategoryInternal("EXPENSE", cat, "", "", now, sortOrder++);
            }
        }
    }

    private CategoryRecord createCategoryInternal(String type, String name, String parentId, String icon,
                                                  LocalDateTime now, int sortOrder) {
        String categoryId = storageService.generateId("CAT");
        LocalExcelStorageService.RowData rowData = new LocalExcelStorageService.RowData();
        rowData.put("CategoryID", categoryId);
        rowData.put("UserID", "");
        rowData.put("Name", name);
        rowData.put("Type", type);
        rowData.put("ParentCategoryID", parentId);
        rowData.put("Icon", icon);
        rowData.put("Color", "");
        rowData.put("IsDefault", "true");
        rowData.put("SortOrder", sortOrder);
        rowData.put("CreatedAt", now.format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        rowData.put("UpdatedAt", now.format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME));

        storageService.appendRow("Categories", rowData);
        return new CategoryRecord(categoryId, "", name, type, parentId, icon, "", true, sortOrder, now, now);
    }

    public CategoryRecord createCategory(User user, String name, String type, String parentId, String icon, String color) {
        name = FinanceValidation.requireText(name, "Category name", 100);
        type = FinanceValidation.requireText(type, "Category type", 10);
        if (!"INCOME".equals(type) && !"EXPENSE".equals(type)) {
            throw new IllegalArgumentException("Type must be INCOME or EXPENSE");
        }
        parentId = parentId != null ? parentId.trim() : "";
        icon = icon != null ? icon.trim() : "";
        color = color != null ? color.trim() : "";

        String categoryId = storageService.generateId("CAT");
        LocalDateTime now = LocalDateTime.now();

        int maxSortOrder = getAllCategories().stream()
                .mapToInt(record -> record.getSortOrder())
                .max()
                .orElse(0);

        LocalExcelStorageService.RowData rowData = new LocalExcelStorageService.RowData();
        rowData.put("CategoryID", categoryId);
        rowData.put("UserID", user != null ? user.getId().toString() : "");
        rowData.put("Name", name);
        rowData.put("Type", type);
        rowData.put("ParentCategoryID", parentId);
        rowData.put("Icon", icon);
        rowData.put("Color", color);
        rowData.put("IsDefault", "false");
        rowData.put("SortOrder", maxSortOrder + 1);
        rowData.put("CreatedAt", now.format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        rowData.put("UpdatedAt", now.format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME));

        storageService.appendRow("Categories", rowData);
        return new CategoryRecord(categoryId, user != null ? user.getId().toString() : "", name, type, parentId, icon, color, false, maxSortOrder + 1, now, now);
    }

    public List<CategoryRecord> getAllCategories() {
        return storageService.readSheet("Categories", row -> mapRowToCategoryRecord(row));
    }

    public List<CategoryRecord> getCategoriesByType(String type) {
        return getAllCategories().stream()
                .filter(c -> type.equalsIgnoreCase(c.getType()))
                .toList();
    }

    public List<CategoryRecord> getUserCategories(User user, String type) {
        return getAllCategories().stream()
                .filter(c -> (user == null || user.getId().toString().equals(c.getUserId()) || c.getUserId().isEmpty()))
                .filter(c -> type == null || type.equalsIgnoreCase(c.getType()))
                .toList();
    }

    public Optional<CategoryRecord> getCategoryById(Long id) {
        return getCategoryById(id.toString());
    }

    public Optional<CategoryRecord> getCategoryById(String id) {
        return storageService.findById("Categories", "CategoryID", id, row -> mapRowToCategoryRecord(row));
    }

    public CategoryRecord updateCategory(User user, String id, String name, String type, String icon, String color) {
        if (user == null || user.getId() == null) {
            throw new IllegalArgumentException("User is required");
        }
        CategoryRecord category = getCategoryById(id)
                .orElseThrow(() -> new IllegalArgumentException("Category not found"));
        if (category.isDefault()) {
            throw new IllegalArgumentException("Cannot edit default categories");
        }
        if (!user.getId().toString().equals(category.getUserId())) {
            throw new IllegalArgumentException("Category not found");
        }

        name = FinanceValidation.requireText(name, "Category name", 100);
        type = FinanceValidation.requireText(type, "Category type", 10).toUpperCase(java.util.Locale.ROOT);
        if (!"INCOME".equals(type) && !"EXPENSE".equals(type)) {
            throw new IllegalArgumentException("Type must be INCOME or EXPENSE");
        }
        icon = FinanceValidation.optionalText(icon, "Category icon", 10);
        color = FinanceValidation.optionalText(color, "Category color", 7);
        if (!color.isEmpty() && !color.matches("^#[0-9A-Fa-f]{6}$")) {
            throw new IllegalArgumentException("Category color must be a hex color");
        }

        LocalDateTime now = LocalDateTime.now();
        LocalExcelStorageService.RowData rowData = new LocalExcelStorageService.RowData();
        rowData.put("CategoryID", category.getCategoryId());
        rowData.put("UserID", category.getUserId());
        rowData.put("Name", name);
        rowData.put("Type", type);
        rowData.put("ParentCategoryID", category.getParentCategoryId());
        rowData.put("Icon", icon);
        rowData.put("Color", color);
        rowData.put("IsDefault", "false");
        rowData.put("SortOrder", category.getSortOrder());
        rowData.put("CreatedAt", category.getCreatedAt().format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        rowData.put("UpdatedAt", now.format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        storageService.updateRow("Categories", "CategoryID", category.getCategoryId(), rowData);

        return new CategoryRecord(category.getCategoryId(), category.getUserId(), name, type,
                category.getParentCategoryId(), icon, color, false, category.getSortOrder(),
                category.getCreatedAt(), now);
    }

    public void deleteCategory(User user, String id) {
        if (user == null || user.getId() == null) {
            throw new IllegalArgumentException("User is required");
        }
        CategoryRecord category = getCategoryById(id)
                .orElseThrow(() -> new IllegalArgumentException("Category not found"));
        if (category.isDefault()) {
            throw new IllegalArgumentException("Cannot delete default categories");
        }
        if (!user.getId().toString().equals(category.getUserId())) {
            throw new IllegalArgumentException("Category not found");
        }
        storageService.deleteRow("Categories", "CategoryID", id);
    }

    private CategoryRecord mapRowToCategoryRecord(Row row) {
        return new CategoryRecord(
                getCellString(row, "CategoryID"),
                getCellString(row, "UserID"),
                getCellString(row, "Name"),
                getCellString(row, "Type"),
                getCellString(row, "ParentCategoryID"),
                getCellString(row, "Icon"),
                getCellString(row, "Color"),
                "true".equalsIgnoreCase(getCellString(row, "IsDefault")),
                parseInt(row, "SortOrder"),
                parseLocalDateTime(row, "CreatedAt"),
                parseLocalDateTime(row, "UpdatedAt")
        );
    }

    private String getCellString(Row row, String columnName) {
        int colIndex = findColumnIndex(row.getSheet(), columnName);
        if (colIndex == -1) return "";
        Cell cell = row.getCell(colIndex);
        return cell != null ? cell.toString().trim() : "";
    }

    private int parseInt(Row row, String columnName) {
        String val = getCellString(row, columnName);
        try {
            return val.isEmpty() ? 0 : Integer.parseInt(val);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private LocalDateTime parseLocalDateTime(Row row, String columnName) {
        String val = getCellString(row, columnName);
        if (val.isEmpty()) return null;
        try {
            return LocalDateTime.parse(val);
        } catch (Exception ignored) {
            return null;
        }
    }

    private int findColumnIndex(Sheet sheet, String columnName) {
        Row headerRow = sheet.getRow(0);
        if (headerRow == null) return -1;
        for (Cell cell : headerRow) {
            if (columnName.equals(cell.getStringCellValue())) {
                return cell.getColumnIndex();
            }
        }
        return -1;
    }

    public static class CategoryRecord {
        private final String categoryId;
        private final String userId;
        private final String name;
        private final String type;
        private final String parentCategoryId;
        private final String icon;
        private final String color;
        private final boolean isDefault;
        private final int sortOrder;
        private final LocalDateTime createdAt;
        private final LocalDateTime updatedAt;

        public CategoryRecord(String categoryId, String userId, String name, String type, String parentCategoryId,
                              String icon, String color, boolean isDefault, int sortOrder,
                              LocalDateTime createdAt, LocalDateTime updatedAt) {
            this.categoryId = categoryId;
            this.userId = userId;
            this.name = name;
            this.type = type;
            this.parentCategoryId = parentCategoryId;
            this.icon = icon;
            this.color = color;
            this.isDefault = isDefault;
            this.sortOrder = sortOrder;
            this.createdAt = createdAt;
            this.updatedAt = updatedAt;
        }

        public String getCategoryId() { return categoryId; }
        public String getUserId() { return userId; }
        public String getName() { return name; }
        public String getType() { return type; }
        public String getParentCategoryId() { return parentCategoryId; }
        public String getIcon() { return icon; }
        public String getColor() { return color; }
        public boolean isDefault() { return isDefault; }
        public int getSortOrder() { return sortOrder; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public LocalDateTime getUpdatedAt() { return updatedAt; }
    }
}