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
        localStorage.setItem(STORAGE_KEY, JSON.stringify(tasks));
        return tasks;
    },

    toggleTask(id) {
        const tasks = this.getTasks();
        const task = tasks.find(t => t.id === id);
        if (task) {
            task.completed = !task.completed;
            localStorage.setItem(STORAGE_KEY, JSON.stringify(tasks));
        }
        return tasks;
    },

    deleteTask(id) {
        let tasks = this.getTasks();
        tasks = tasks.filter(t => t.id !== id);
        localStorage.setItem(STORAGE_KEY, JSON.stringify(tasks));
        return tasks;
    }
};
