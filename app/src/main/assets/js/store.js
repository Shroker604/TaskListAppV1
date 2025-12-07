// Store.js - Manages data persistence
const STORAGE_KEY = 'ai_tasklist_v1';

const Store = {
    getTasks() {
        const tasks = localStorage.getItem(STORAGE_KEY);
        return tasks ? JSON.parse(tasks) : [];
    },

    saveTask(task) {
        const tasks = this.getTasks();
        tasks.push(task); // Add to end
        this._persist(tasks);
        return tasks;
    },

    toggleTask(id) {
        const tasks = this.getTasks();
        const task = tasks.find(t => t.id === id);
        if (task) {
            task.completed = !task.completed;
            this._persist(tasks);
        }
        return tasks;
    },

    deleteTask(id) {
        let tasks = this.getTasks();
        tasks = tasks.filter(t => t.id !== id);
        this._persist(tasks);
        return tasks;
    },

    _persist(tasks) {
        const json = JSON.stringify(tasks);
        localStorage.setItem(STORAGE_KEY, json);

        // Native Bridge Call
        if (window.Android && window.Android.updateWidgets) {
            window.Android.updateWidgets(json);
        }
    },

    getApiKey() {
        return localStorage.getItem('gemini_api_key') || '';
    },

    saveApiKey(key) {
        localStorage.setItem('gemini_api_key', key);
    },

    getTheme() {
        return localStorage.getItem('theme') || 'dark';
    },

    saveTheme(theme) {
        localStorage.setItem('theme', theme);
    }
};
