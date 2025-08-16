import sys
import pandas as pd
import matplotlib.pyplot as plt
import matplotlib.patches as mpatches

def create_box_plot(data_csv, output_path):
    """
    Generates a box plot for per-base sequence quality from pre-calculated statistics.

    Args:
        data_csv (str): A string containing the data in CSV format.
        output_path (str): The path to save the generated plot image.
    """
    from io import StringIO
    df = pd.read_csv(StringIO(data_csv))

    if df.empty:
        print("Input data is empty. No plot generated.")
        return

    # Matplotlib's bxp function requires data as a list of dictionaries
    stats = []
    for i, row in df.iterrows():
        stat_dict = {
            'med': row['median'], # Median
            'q1': row['q1'],      # 1st Quartile (25th percentile)
            'q3': row['q3'],      # 3rd Quartile (75th percentile)
            'whislo': row['q10'], # Lower whisker (10th percentile)
            'whishi': row['q90'], # Upper whisker (90th percentile)
            'fliers': []          # No outliers as we are using pre-calculated stats
        }
        stats.append(stat_dict)

    fig, ax = plt.subplots(figsize=(12, 7))

    # Use bxp to plot from our pre-calculated statistics
    box_plot = ax.bxp(stats, showfliers=False, patch_artist=True)

    # --- Aesthetics ---
    plt.style.use('seaborn-v0_8-whitegrid')

    # Set colors for boxes and whiskers
    for element in ['boxes', 'whiskers', 'caps', 'medians']:
        for patch in box_plot[element]:
            if element == 'boxes':
                patch.set_facecolor('orange') # Changed to orange
                patch.set_edgecolor('black')
                patch.set_alpha(0.7)
            elif element == 'medians':
                patch.set_color('red')
                patch.set_linewidth(1.5)
            else:
                patch.set_color('black')
                patch.set_linewidth(1)

    # Add background color zones for quality
    ax.axhspan(0, 20, facecolor='#FFDDDD', alpha=0.4, zorder=0)  # Poor quality (Red zone)
    ax.axhspan(20, 28, facecolor='#FFFFCC', alpha=0.4, zorder=0) # Acceptable quality (Yellow zone)
    ax.axhspan(28, 42, facecolor='#DDFFDD', alpha=0.4, zorder=0) # Good quality (Green zone)

    # Set titles and labels
    ax.set_title('Per Base Sequence Quality', fontsize=16, fontweight='bold')
    ax.set_xlabel('Position in Read (bp)', fontsize=12)
    ax.set_ylabel('Phred Quality Score', fontsize=12)

    # Configure axes
    ax.set_ylim(0, 42)
    ax.set_xlim(0, len(df['position']) + 1)

    # Set x-axis ticks to be the positions from the data
    tick_positions = df['position'].tolist()
    ax.set_xticks(tick_positions)
    ax.set_xticklabels([str(pos) for pos in tick_positions], rotation=90, fontsize=8)

    # Reduce tick density for readability if there are many positions
    if len(tick_positions) > 50:
        for i, label in enumerate(ax.get_xticklabels()):
            if i % 5 != 0:
                label.set_visible(False)

    # Create a legend
    legend_patches = [
        mpatches.Patch(color='#DDFFDD', label='Good'),
        mpatches.Patch(color='#FFFFCC', label='Okay'),
        mpatches.Patch(color='#FFDDDD', label='Poor')
    ]
    ax.legend(handles=legend_patches, loc='lower left') # Changed to lower left

    fig.tight_layout()

    # Save the figure
    plt.savefig(output_path, dpi=150)
    plt.close(fig)
    print(f"Box plot saved to {output_path}")


if __name__ == '__main__':
    if len(sys.argv) != 2:
        print("Usage: python create_box_plot.py <output_path>")
        sys.exit(1)

    output_file_path = sys.argv[1]
    csv_input_data = sys.stdin.read()

    create_box_plot(csv_input_data, output_file_path)
