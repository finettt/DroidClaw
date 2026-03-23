---
name: data_processing
version: 1.0.0
description: Process and analyze structured data
author: system
category: data
enabled: true
required_tools:
  - read_file
  - write_file
  - execute_shell
tags:
  - data
  - transformation
  - analysis
---

# Data Processing Skill

This skill enables you to process, transform, and analyze structured data in various formats including CSV, JSON, and text files.

## Capabilities

- Read and parse CSV, JSON, and other structured formats
- Transform data between formats
- Filter and sort data
- Calculate statistics and aggregations
- Generate summaries and reports
- Handle large datasets efficiently

## Available Tools

### File Operations
```bash
read_file(path="data.csv")
write_file(path="output.json", content="...")
```

### Shell Commands for Data
```bash
# Sort data
sort data.csv

# Get unique values
cut -d',' -f1 data.csv | sort | uniq

# Count lines
wc -l data.csv

# Filter with grep
grep "pattern" data.csv
```

## Guidelines

1. First understand the data structure by reading a sample
2. Use appropriate parsing for each format:
   - CSV: Split by delimiter, handle quoted fields
   - JSON: Parse with JSON library or tools
   - Fixed-width: Extract by character positions
3. For large files, read in chunks or use line-based processing
4. Validate data before processing
5. Report data quality issues (missing values, format errors)
6. Provide summary statistics when appropriate

## Common Operations

### Data Filtering
- Filter rows by condition
- Select specific columns
- Remove duplicates

### Data Transformation
- Convert between formats
- Rename columns
- Calculate derived fields

### Data Analysis
- Count records
- Calculate averages/sums
- Find min/max values
- Identify trends

## Example Usage

**User:** "Process this CSV and count unique values in column 2"

**Your approach:**
1. Read the CSV file
2. Extract column 2 values
3. Use shell command to count unique values
4. Present results

**User:** "Convert this JSON to CSV"

**Your approach:**
1. Read the JSON file
2. Parse JSON structure
3. Transform to CSV format
4. Write to output file

## Limitations

- Large files may require chunked processing
- Complex transformations may need multiple steps
- No built-in statistical analysis library
- Shell commands limited to available tools