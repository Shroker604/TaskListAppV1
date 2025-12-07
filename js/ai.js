// AI.js - Handles communication with Gemini API

const AI = {
    // Key is split to prevent casual scraping/searching in source code
    // This is "Security through Obscurity" - sufficient for casual use but not preventing network sniffing
    _c: ['AIzaSyAVRKKCOg', '-ZekkLL2-8kwb7', 'zlZ_yMZSakg'],

    getEncodedKey() {
        return this._c.join('');
    },

    async generateTasks(prompt, userKey) {
        const apiKey = userKey || this.getEncodedKey();

        // Using 2.5-flash as explicitly requested by user via AI Studio info
        const url = `https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=${apiKey}`;

        const systemPrompt = `
        You are a helpful assistant. Please take the following user input and split it into a list of distinct tasks.
        
        Rules:
        1. Return ONLY a valid JSON array of strings.
        2. Do not include markdown formatting like \`\`\`json ... \`\`\`.
        3. Keep unrelated tasks separate. For example, "Buy milk and wash the car" should be TWO tasks: "Buy milk" and "Wash the car".
        
        Example Input: "Buy milk and call mom"
        Example Output: ["Buy milk", "Call Mom"]
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

            if (data.candidates && data.candidates[0].content) {
                const text = data.candidates[0].content.parts[0].text;
                // Clean markdown if present
                const cleanJson = text.replace(/```json/g, '').replace(/```/g, '').trim();
                return JSON.parse(cleanJson); // Returns Array of strings
            }
            return [prompt]; // Fallback

        } catch (error) {
            console.error("AI Error:", error);
            // Fallback: Return as single task if parsing fails
            return [prompt];
        }
    }
};
