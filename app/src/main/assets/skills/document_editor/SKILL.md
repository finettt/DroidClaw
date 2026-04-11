---
name: document_editor
description: Create, read, and modify Word (.docx) and Excel (.xlsx/.xls) documents. Use when the user wants to work with office documents.
category: document-processing
tags: word, excel, docx, xlsx, office, document, spreadsheet
required_tools: execute_python, read_file, write_file
---

# Document Editor Skill

This skill enables you to work with Microsoft Word (.docx) and Excel (.xlsx) documents using Python libraries `python-docx` and `openpyxl`.

## When to Use This Skill

Use this skill when:
- Reading or extracting content from .docx or .xlsx files
- Creating new Word or Excel documents
- Modifying existing documents (editing text, updating cells, formatting)
- Converting between document formats
- Analyzing or transforming spreadsheet data
- Generating reports or tables

## Capabilities

- **Excel (.xlsx)**: Read/write workbooks, access sheets and cells, manipulate data, apply formulas, format cells
- **Word (.docx)**: Read/write documents, work with paragraphs and tables, manage styles and formatting
- **Data transformation**: Convert Excel data to Word tables, generate reports from spreadsheets
- **Batch operations**: Process multiple documents at once

## Guidelines

### General Approach

1. Identify the document type (.docx vs .xlsx) and required operation
2. Read the file first to understand its structure if modifying
3. Use Python's `execute_python` tool with appropriate library
4. Save the result to a new file or overwrite (confirm with user)
5. Return the file to the user or confirm changes

### Working with Excel Files (.xlsx)

Use `openpyxl` library. Example patterns:

```python
# Reading an Excel file
from openpyxl import load_workbook

wb = load_workbook('file.xlsx')
sheet = wb.active  # or wb['SheetName']

# Access cells
value = sheet['A1'].value
row_data = [cell.value for cell in sheet[1]]  # First row

# Modify cells
sheet['A1'] = 'New Value'
sheet.append(['Col1', 'Col2', 'Col3'])  # New row

# Save
wb.save('output.xlsx')
```

**Common operations:**
- Read entire sheet: iterate `sheet.iter_rows()` or `sheet.iter_cols()`
- Get dimensions: `sheet.max_row`, `sheet.max_column`
- Update cell values: `sheet.cell(row=1, column=1, value='New')`
- Apply formulas: `sheet['A1'] = '=SUM(B1:B10)'`
- Format cells: use `openpyxl.styles` (Font, PatternFill, Alignment, Border)

### Working with Word Documents (.docx)

Use `docx` library (python-docx). Example patterns:

```python
# Reading a Word document
from docx import Document

doc = Document('file.docx')

# Access paragraphs
for para in doc.paragraphs:
    print(para.text)

# Access tables
for table in doc.tables:
    for row in table.rows:
        for cell in row.cells:
            print(cell.text)

# Modify document
doc.add_paragraph('New paragraph')
doc.add_table(rows=3, cols=3)

# Save
doc.save('output.docx')
```

**Common operations:**
- Add paragraph: `doc.add_paragraph('Text')`
- Add heading: `doc.add_heading('Title', level=1)`
- Add table: `table = doc.add_table(rows=2, cols=3)` then fill cells
- Modify existing: iterate and update `para.text` or `cell.text`
- Apply styles: use `para.style`, `run.bold`, `run.font_size`

## Example Usage

**User:** "Read this Excel file and tell me what's in it"

**Your approach:**
1. Use `execute_python` with script to load and inspect the file
2. Print sheet names, dimensions, sample data
3. Present findings to user

**User:** "Update the total column in this spreadsheet"

**Your approach:**
1. Read the file to understand structure
2. Calculate new totals
3. Update cells with formula or calculated values
4. Save and confirm

**User:** "Create a Word document with a summary table"

**Your approach:**
1. Create new Document
2. Add heading and paragraphs
3. Create table with data
4. Save and provide to user

**User:** "Convert this Excel data to a Word table"

**Your approach:**
1. Read Excel data with openpyxl
2. Create Word document with python-docx
3. Create table in Word with same dimensions
4. Copy data from Excel to Word table
5. Save result

## Limitations

- Cannot preserve all original formatting when modifying documents
- Complex Excel features (macros, charts, pivot tables) not supported
- Images in Word documents require additional handling
- Large documents may hit execution timeout (default 30s, max 300s)
- Only supports .xlsx format (not legacy .xls)
- Only supports .docx format (not legacy .doc)
- Style preservation may be limited
