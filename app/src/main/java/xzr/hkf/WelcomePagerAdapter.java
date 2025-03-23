package xzr.hkf;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

public class WelcomePagerAdapter extends RecyclerView.Adapter<WelcomePagerAdapter.WelcomeViewHolder> {

    private final Context context;
    private final int[] titles = {R.string.welcome_title_1, R.string.welcome_title_2, R.string.welcome_title_3};
    private final int[] descriptions = {R.string.welcome_desc_1, R.string.welcome_desc_2, R.string.welcome_desc_3};

    public WelcomePagerAdapter(Context context) {
        this.context = context;
    }

    @NonNull
    @Override
    public WelcomeViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.welcome_page_item, parent, false);
        return new WelcomeViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull WelcomeViewHolder holder, int position) {
        holder.titleTextView.setText(titles[position]);
        holder.descriptionTextView.setText(descriptions[position]);
        
        // Apply slide-up animation with fade for text
        holder.itemView.setTranslationY(50);
        holder.itemView.setAlpha(0f);
        holder.itemView.animate()
                .translationY(0)
                .alpha(1f)
                .setDuration(500)
                .setInterpolator(new android.view.animation.DecelerateInterpolator())
                .start();
    }

    @Override
    public int getItemCount() {
        return titles.length;
    }

    static class WelcomeViewHolder extends RecyclerView.ViewHolder {
        TextView titleTextView;
        TextView descriptionTextView;

        public WelcomeViewHolder(@NonNull View itemView) {
            super(itemView);
            titleTextView = itemView.findViewById(R.id.welcome_page_title);
            descriptionTextView = itemView.findViewById(R.id.welcome_page_description);
        }
    }
} 