import matplotlib.pyplot as plt
import sys
import pandas as pd

def create_table_image(output_image_path):
    """
    Reads tab-separated values from stdin and renders them as a table image.
    Dynamically adjusts to the number of columns in the header.
    """
    try:
        lines = sys.stdin.readlines()
        if not lines or len(lines) < 2:
            print("Error: No data or header received from stdin.")
            sys.exit(1)

        # --- FIX: Dynamically determine columns from the header ---
        header = [h.strip() for h in lines[0].strip().split('\t')]
        num_columns = len(header)

        data = []
        # Process the remaining lines
        for line in lines[1:]:
            if line.strip(): # Ensure the line is not empty
                parts = [p.strip() for p in line.strip().split('\t')]
                # Pad row with empty strings if it has fewer columns than the header
                while len(parts) < num_columns:
                    parts.append('')
                data.append(parts[:num_columns])

        if not data:
            print("Error: No data rows found.")
            sys.exit(1)

        # --- FIX: Create the plot and table with dynamic columns ---
        fig, ax = plt.subplots(figsize=(8, len(data) * 0.5 + 1)) # Adjust size based on data rows
        ax.axis('off')
        ax.axis('tight')

        table = ax.table(cellText=data,
                         colLabels=header,
                         loc='center',
                         cellLoc='left')

        # Styling
        table.auto_set_font_size(False)
        table.set_fontsize(11)
        table.scale(1.1, 1.6)

        for (i, j), cell in table.get_celld().items():
            cell.set_edgecolor('w')
            if i == 0:  # Header row
                cell.set_text_props(weight='bold', color='white')
                cell.set_facecolor('#2C3E50')
            else:  # Data rows
                cell.set_facecolor('#ECF0F1')

        plt.savefig(output_image_path, dpi=120, bbox_inches='tight', pad_inches=0.4)
        print(f"Successfully saved table image to {output_image_path}")

    except Exception as e:
        print(f"An error occurred in create_table.py: {e}", file=sys.stderr)
        sys.exit(1)

if __name__ == '__main__':
    if len(sys.argv) != 2:
        print("Usage: python create_table.py <output_image_path>", file=sys.stderr)
        sys.exit(1)

    create_table_image(sys.argv[1])