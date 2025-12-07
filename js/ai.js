// AI.js - Handles communication with Gemini API

const AI = {
    async generateTask(prompt, apiKey) {
        if (!apiKey) throw new Error("API Key missing");

        const url = `https://generativelanguage.googleapis.com/v1beta/models/gemini-pro:generateContent?key=${apiKey}`;

        const systemPrompt = `
        You are an intelligent task parsing assistant. 
        Your goal is to extract a single task from the user's natural language input.
        If the input contains multiple tasks, combine them logically or pick the main one.
        
        Return STRICT JSON format:
        { "text": "The concise task description" }
        
        Example Input: "Remind me to buy milk tomorrow heavily"
        Example Output: { "text": "Buy milk" }
        `;

        const payload = {
            contents: [{
                parts: [{
                    text: `${systemPrompt}\n\nUser Input: ${prompt}`
                }]
            }]
        };

        try {
            const response = await fetch(url, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(payload)
            });

            const data = await response.json();

            if (!response.ok) throw new Error(data.error?.message || "API Error");

            const text = data.candidates[0].content.parts[0].text;
            // Clean markdown if present
            const cleanJson = text.replace(/```json/g, '').replace(/```/g, '').trim();
            return JSON.parse(cleanJson);

        } catch (error) {
            console.error("AI Error:", error);
            throw error;
        }
    }
};
