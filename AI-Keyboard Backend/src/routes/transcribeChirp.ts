import { Request, Response } from "express";
import multer from "multer";
import { v4 as uuidv4 } from "uuid";
import path from "path";
import fs from "fs/promises";
import crypto from "crypto";
import { chirpAdapter } from "../lib/chirpAdapter.js";
import { createLLMAdapter } from "../lib/llmAdapter.js";
import { logger } from "../lib/logger.js";
import { authenticateClient } from "../lib/auth.js";

// Configure multer for file uploads
const upload = multer({
    storage: multer.diskStorage({
        destination: "./tmp/audio-uploads/",
        filename: (req, file, cb) => {
            // Get original extension
            const ext = path.extname(file.originalname) || "";
            // Generate unique filename with extension
            cb(null, `${uuidv4()}${ext}`);
        },
    }),
    limits: {
        fileSize: 100 * 1024 * 1024, // 100MB limit
    },
    fileFilter: (req, file, cb) => {
        const allowedMimes = [
            "audio/wav",
            "audio/wave",
            "audio/x-wav",
            "audio/ogg",
            "audio/opus",
            "audio/m4a",
            "audio/mp4",
            "audio/mpeg",
        ];
        if (allowedMimes.includes(file.mimetype)) {
            cb(null, true);
        } else {
            cb(new Error(`Unsupported audio format: ${file.mimetype}`));
        }
    },
});

export const transcribeChirpHandler = async (req: Request, res: Response) => {
    const startTime = Date.now();
    let tempAudioFile: string | null | undefined = null;

    // Get request ID early so it's available for all error responses
    const requestId = req.body.requestId || uuidv4();
    const fileUrl = req.protocol + "://" + req.get("host") + "/audio-files/" + req?.file?.filename;
    // Get formatting preference from request body (default to true for backward compatibility)
    // Convert string to boolean since multer sends form data as strings
    const enableFormatting = req.body.enableFormatting !== "false";
    tempAudioFile = req?.file?.path;
    console.log("Temporary audio file path:", tempAudioFile);
    // Get custom prompt template and mode title from form data
    const promptTemplate = req.body.promptTemplate;
    const modeTitle = req.body.modeTitle;

    // Validate prompt template if provided
    if (promptTemplate && promptTemplate.length > parseInt(process.env.MAX_PROMPT_TEMPLATE_CHARS || "4000")) {
        return res.status(400).json({
            error: "Bad Request",
            message: `promptTemplate cannot exceed ${process.env.MAX_PROMPT_TEMPLATE_CHARS || "4000"} characters`,
            requestId,
        });
    }

    logger.info("Chirp transcription request received", {
        requestId,
        enableFormatting,
        rawEnableFormatting: req.body.enableFormatting,
        hasCustomPrompt: !!promptTemplate,
        modeTitle,
    });

    try {
        // Validate file upload first
        if (!req.file) {
            return res.status(400).json({
                error: "No audio file provided",
                code: "MISSING_AUDIO_FILE",
                requestId,
            });
        }

        // Check if Chirp is available
        const isChirpAvailable = await chirpAdapter.isAvailable();
        if (!isChirpAvailable) {
            logger.error("Chirp adapter not available");
            return res.status(502).json({
                error: "Transcription service unavailable",
                code: "CHIRP_UNAVAILABLE",
                requestId,
            });
        }

        tempAudioFile = req.file.path;
        logger.info("Processing audio file for Chirp transcription", {
            requestId,
            filename: req.file.originalname,
            size: req.file.size,
            mimetype: req.file.mimetype,
        });

        // Get language from query parameter or use auto
        const languageCode = (req.query.language as string) || "auto";

        // Transcribe with Chirp
        const transcriptionResult = await chirpAdapter.transcribeAudio(fileUrl, languageCode);

        if (!transcriptionResult.transcript) {
            logger.warn("Empty transcription result", { requestId });
            return res.status(400).json({
                error: "No speech detected in audio",
                code: "NO_SPEECH_DETECTED",
                requestId,
            });
        }

        logger.info("Chirp transcription completed", {
            requestId,
            transcriptLength: transcriptionResult.transcript.length,
            processingTime: Date.now() - startTime,
            enableFormatting,
        });

        let formattedText: string;
        let usage: any = undefined;

        if (enableFormatting) {
            // Format the transcript using LLM adapter
            const llmAdapter = createLLMAdapter();

            if (promptTemplate) {
                // Log custom prompt info (safely)
                const promptPreview = promptTemplate.substring(0, 200);
                const promptHash = crypto.createHash("sha256").update(promptTemplate).digest("hex").substring(0, 8);
                logger.info("Custom prompt template provided for Chirp transcription", {
                    requestId,
                    modeTitle,
                    promptPreview,
                    promptHash,
                    promptLength: promptTemplate.length,
                });

                // Process custom prompt template
                let processedTemplate = promptTemplate;

                // Auto-append transcript placeholder if missing
                if (!processedTemplate.includes("{{transcript}}")) {
                    processedTemplate = `${processedTemplate}\n\nTranscript: {{transcript}}`;
                    logger.info("Auto-appended transcript placeholder to custom prompt for Chirp", {
                        requestId,
                        modeTitle,
                    });
                }

                const formattingResult = await llmAdapter.formatTextWithCustomPrompt(
                    transcriptionResult.transcript,
                    processedTemplate
                );
                formattedText = formattingResult.text;
                usage = formattingResult.usage;
            } else {
                // Use default formatting
                const formattingResult = await llmAdapter.formatText(transcriptionResult.transcript);
                formattedText = formattingResult.text;
                usage = formattingResult.usage;
            }
        } else {
            // Use raw transcription without formatting
            formattedText = transcriptionResult.transcript;
        }

        const response = {
            formattedText,
            requestId,
            modeTitle,
            rawTranscription: transcriptionResult.transcript,
            usage,
            processingTime: Date.now() - startTime,
        };

        logger.info("Chirp transcription completed", {
            requestId,
            totalProcessingTime: response.processingTime,
            formattedTextLength: response.formattedText.length,
            formattingApplied: enableFormatting,
            modeTitle,
        });

        return res.json(response);
    } catch (error) {
        const errorMessage = error instanceof Error ? error.message : String(error);
        const errorStack = error instanceof Error ? error.stack : undefined;

        logger.error("Chirp transcription failed", {
            error: errorMessage,
            stack: errorStack,
            requestId,
        });

        // Return appropriate error based on error type
        if (errorMessage.includes("auth") || errorMessage.includes("permission")) {
            return res.status(502).json({
                error: "Transcription auth error",
                code: "CHIRP_AUTH_ERROR",
                requestId,
            });
        }

        if (errorMessage.includes("timeout")) {
            return res.status(504).json({
                error: "Transcription timeout",
                code: "CHIRP_TIMEOUT",
                requestId,
            });
        }

        return res.status(500).json({
            error: "Transcription failed",
            code: "CHIRP_ERROR",
            requestId,
        });
    } finally {
        // Clean up temporary audio file
        if (tempAudioFile) {
            try {
                await fs.unlink(tempAudioFile);
                logger.debug("Cleaned up temporary audio file", { tempAudioFile });
            } catch (cleanupError) {
                const cleanupErrorMessage = cleanupError instanceof Error ? cleanupError.message : String(cleanupError);
                logger.warn("Failed to clean up temporary audio file", {
                    tempAudioFile,
                    error: cleanupErrorMessage,
                });
            }
        }
    }
};

// Export multer middleware for use in route setup
export { upload };
