// Render.js - UI Rendering Logic
const Render = {
    tasks(tasks, container) {
        container.innerHTML = '';

        if (tasks.length === 0) {
            container.innerHTML = `
                <div class="empty-state fade-in" style="text-align:center; margin-top: 40px; color: var(--text-muted);">
                    <p>No tasks yet.</p>
                    <p style="font-size: 0.9rem; margin-top:8px;">Tap + to add one.</p>
                </div>
            `;
            return;
        }

        // Sort: Incomplete first, then by date
        const sortedTasks = [...tasks].sort((a, b) => {
            if (a.completed === b.completed) return b.id - a.id; // Newest first
            return a.completed ? 1 : -1;
        });

        sortedTasks.forEach(task => {
            const el = document.createElement('div');
            el.className = `task-item slide-up ${task.completed ? 'completed' : ''}`;
            el.dataset.id = task.id;

            el.innerHTML = `
                <div class="checkbox">
                    ${task.completed ? '<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="white" stroke-width="4" stroke-linecap="round" stroke-linejoin="round"><polyline points="20 6 9 17 4 12"></polyline></svg>' : ''}
                </div>
                <span class="task-text">${this.escapeHtml(task.text)}</span>
            `;

            container.appendChild(el);
        });
    },

    escapeHtml(text) {
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }
};
