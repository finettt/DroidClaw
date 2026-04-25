---
name: presentation-editor
description: Create, read, and modify PowerPoint (.pptx) presentations. Use when the user wants to build or edit slide decks, add charts, images, tables, or formatted text to presentations.
category: document-processing
tags: powerpoint, pptx, presentation, slides, office, charts, tables
required_tools: execute_python, read_file, write_file
---

# Presentation Editor Skill

This skill enables you to create and manipulate Microsoft PowerPoint (.pptx) presentations using the `python-pptx` library.

## When to Use This Skill

Use this skill when:
- Creating a new PowerPoint presentation from scratch
- Reading or extracting content (text, tables, notes) from an existing .pptx file
- Adding, removing, or reordering slides
- Inserting or modifying text boxes, titles, and body content
- Adding tables, images, or charts to slides
- Applying themes, slide layouts, and formatting
- Generating slide decks from structured data (e.g., from a list or spreadsheet)
- Converting data to a visual summary slide deck

## Capabilities

- **Slides**: Add, remove, reorder slides; apply built-in slide layouts
- **Text**: Insert and format text frames, titles, body text, bullet lists
- **Tables**: Create and populate tables within slides
- **Images**: Insert images from file paths into slides
- **Charts**: Add basic bar, line, and pie charts from data
- **Notes**: Read and write slide speaker notes
- **Themes**: Use built-in layouts from an existing theme or blank presentation
- **Batch generation**: Produce multi-slide decks from data structures

## Guidelines

### General Approach

1. Determine the operation: create new, read/inspect, or modify existing
2. If modifying, read the file first to understand slide count and layout
3. Use `execute_python` with `python-pptx` code
4. Save the result to a named `.pptx` file
5. Report the output path and a summary of changes to the user

### Creating a New Presentation

```python
from pptx import Presentation
from pptx.util import Inches, Pt

prs = Presentation()

# Use a built-in slide layout (0 = title slide, 1 = title+content, etc.)
slide_layout = prs.slide_layouts[0]
slide = prs.slides.add_slide(slide_layout)

# Set title and subtitle
title = slide.shapes.title
subtitle = slide.placeholders[1]
title.text = "My Presentation"
subtitle.text = "Created with python-pptx"

prs.save("output.pptx")
print("Saved: output.pptx")
```

### Reading an Existing Presentation

```python
from pptx import Presentation

prs = Presentation("input.pptx")

print(f"Slides: {len(prs.slides)}")

for i, slide in enumerate(prs.slides):
    print(f"\n--- Slide {i + 1} ---")
    for shape in slide.shapes:
        if shape.has_text_frame:
            for para in shape.text_frame.paragraphs:
                print(para.text)
        if shape.has_table:
            tbl = shape.table
            for row in tbl.rows:
                print([cell.text for cell in row.cells])
    # Speaker notes
    if slide.has_notes_slide:
        notes = slide.notes_slide.notes_text_frame.text
        if notes.strip():
            print(f"Notes: {notes}")
```

### Adding a Content Slide with Bullet Points

```python
from pptx import Presentation
from pptx.util import Pt

prs = Presentation("existing.pptx")

# layout index 1 is typically "Title and Content"
layout = prs.slide_layouts[1]
slide = prs.slides.add_slide(layout)

slide.shapes.title.text = "Key Points"

tf = slide.placeholders[1].text_frame
tf.text = "First bullet point"

p = tf.add_paragraph()
p.text = "Second bullet point"
p.level = 1  # sub-bullet

p2 = tf.add_paragraph()
p2.text = "Third bullet point"

prs.save("existing.pptx")
print("Updated presentation saved.")
```

### Inserting a Table into a Slide

```python
from pptx import Presentation
from pptx.util import Inches

prs = Presentation()
slide = prs.slides.add_slide(prs.slide_layouts[5])  # Blank layout

rows, cols = 4, 3
left, top, width, height = Inches(1), Inches(2), Inches(8), Inches(3)
table = slide.shapes.add_table(rows, cols, left, top, width, height).table

# Header row
headers = ["Name", "Score", "Grade"]
for col_idx, header in enumerate(headers):
    table.cell(0, col_idx).text = header

# Data rows
data = [
    ("Alice", "92", "A"),
    ("Bob", "78", "B"),
    ("Carol", "85", "B+"),
]
for row_idx, (name, score, grade) in enumerate(data, start=1):
    table.cell(row_idx, 0).text = name
    table.cell(row_idx, 1).text = score
    table.cell(row_idx, 2).text = grade

prs.save("table_slide.pptx")
print("Saved: table_slide.pptx")
```

### Adding a Chart (Bar Chart Example)

```python
from pptx import Presentation
from pptx.util import Inches
from pptx.chart.data import ChartData
from pptx.enum.chart import XL_CHART_TYPE

prs = Presentation()
slide = prs.slides.add_slide(prs.slide_layouts[5])

chart_data = ChartData()
chart_data.categories = ["Q1", "Q2", "Q3", "Q4"]
chart_data.add_series("Revenue", (120, 145, 132, 178))

left, top, width, height = Inches(1), Inches(1.5), Inches(8), Inches(5)
slide.shapes.add_chart(
    XL_CHART_TYPE.BAR_CLUSTERED,
    left, top, width, height,
    chart_data
)

prs.save("chart_slide.pptx")
print("Saved: chart_slide.pptx")
```

### Inserting an Image

```python
from pptx import Presentation
from pptx.util import Inches

prs = Presentation()
slide = prs.slides.add_slide(prs.slide_layouts[5])

# img_path must be accessible on the device filesystem
img_path = "/path/to/image.png"
slide.shapes.add_picture(img_path, Inches(1), Inches(1), width=Inches(6))

prs.save("image_slide.pptx")
print("Saved: image_slide.pptx")
```

### Common Slide Layout Indices

| Index | Name |
|-------|------|
| 0 | Title Slide |
| 1 | Title and Content |
| 2 | Title and Two Content |
| 3 | Title Only |
| 4 | Blank |
| 5 | Content with Caption |
| 6 | Picture with Caption |

> Note: Layout indices can vary with the theme. Inspect `prs.slide_layouts` to confirm.

## Example Usage

**User:** "Create a 3-slide summary presentation about our Q1 results"

**Your approach:**
1. Create a new `Presentation()`
2. Slide 1 (Title Slide): title = "Q1 Results", subtitle = current date/context
3. Slide 2 (Title + Content): key bullet points
4. Slide 3 (Title + Content): conclusion / next steps
5. Save and report filename

**User:** "Read this .pptx and tell me what's on each slide"

**Your approach:**
1. Use `execute_python` to open and iterate the presentation
2. Print title and body text per slide
3. Report findings to the user

**User:** "Add a chart slide showing monthly sales to my existing deck"

**Your approach:**
1. Open the existing `.pptx` with `Presentation("file.pptx")`
2. Add a new slide with `slide_layouts[5]` (blank) or `[1]` (title+content)
3. Build `ChartData` with month labels and values
4. Call `add_chart()` and save

**User:** "Generate a slide deck from this data table"

**Your approach:**
1. Parse the data (from text, CSV string, or prior tool output)
2. Create one summary slide per section or row group
3. Use tables or bullet lists as appropriate
4. Save the resulting `.pptx`

## Limitations

- Only supports `.pptx` format (not legacy `.ppt`)
- Embedded media (audio, video) cannot be created, only preserved if already in the file
- Complex animations and slide transitions cannot be programmatically defined
- Font embedding depends on the fonts available on the device
- Chart types are limited to those supported by `python-pptx` (most common types are available)
- Large presentations with many images may hit execution timeout (default 30s, max 300s)
- Modifying existing complex layouts may produce unexpected results; creating fresh slides is more reliable