import { Request, Response, Router } from "express";
import path from "path";
import fs from "fs/promises";
import { logger } from "../lib/logger.js";
import { authenticateClient } from "../lib/auth.js";

const router = Router();

// Serve audio files from the uploads directory
router.get("/:filename", async (req: Request, res: Response) => {
    const filename = req.params.filename;
    if (!filename) {
        return res.status(400).json({
            error: "Bad Request",
            message: "Filename is required",
        });
    }
    const filePath = path.join(process.cwd(), "tmp", "audio-uploads", filename);

    try {
        // Check if file exists
        await fs.access(filePath);

        // Log the file access
        logger.info("Serving audio file", {
            filename,
            filePath,
        });
        let extension = path.extname(filename).toLowerCase();
        // Set appropriate headers for audio files
        res.setHeader("Content-Type", `audio/${extension}`);
        res.setHeader("Content-Disposition", `attachment; filename="${filename}"`);

        // Stream the file to the response
        return res.sendFile(filePath);
    } catch (error) {
        const errorMessage = error instanceof Error ? error.message : String(error);

        logger.error("Error serving audio file", {
            filename,
            error: errorMessage,
        });

        return res.status(404).json({
            error: "File not found",
            message: `Audio file ${filename} does not exist`,
        });
    }
});

export default router;
