package tools.vitruv.domains.java.util.gitchangereplay.extractors

import com.google.common.collect.Lists
import tools.vitruv.domains.java.util.gitchangereplay.ScmChangeResult
import java.io.ByteArrayOutputStream
import java.util.ArrayList
import java.util.NoSuchElementException
import org.apache.log4j.Level
import org.apache.log4j.Logger
import org.eclipse.core.runtime.IPath
import org.eclipse.core.runtime.Path
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.diff.DiffEntry
import org.eclipse.jgit.diff.RenameDetector
import org.eclipse.jgit.errors.MissingObjectException
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.treewalk.CanonicalTreeParser

class GitChangeExtractor implements IScmChangeExtractor<ObjectId> {
	
	private static final Logger logger = Logger.getLogger(typeof(GitChangeExtractor))
	
	private Repository repository
	
	private IPath projectRepoOffset
	
	new(Repository repository, IPath projectRepoOffset) {
		logger.level = Level.ALL
		this.repository = repository
		this.projectRepoOffset = projectRepoOffset
	}
	
	def <T> reverse(Iterable<T> iterable) {
		val toReverse = new ArrayList<T>()
		for (element : iterable) {
			toReverse.add(element)
		}
		val reversed = Lists.reverse(toReverse)
		return reversed as Iterable<T>
	}
	
	def pathExists(ObjectId from, ObjectId to) {
		val checkWalk = new RevWalk(repository)
		return checkWalk.isMergedInto(checkWalk.parseCommit(from),
			checkWalk.parseCommit(to))
	}
	
	def getNextCommit(RevCommit toCommit, ObjectId oldVersion) {
		for (child : toCommit.parents) {
			if (pathExists(oldVersion, child.id)) {
				if (toCommit.parentCount > 1) {
					logger.warn("Commit " + toCommit + " has multiple parents. Choosing random path to parent: " + child +
						" Changes in other path will only be applied during merge. Not as atomic as possible...")
				}
				return child
			}
		}
	}
	
	override extract(ObjectId newVersion, ObjectId oldVersion) {
		val reader = repository.newObjectReader()
		val git = new Git(repository)
		try {
			val walk = new RevWalk(repository)
			var reachedOldVersion = false
			val newestCommit = walk.parseCommit(newVersion)
			val oldestCommit = walk.parseCommit(oldVersion)
			val commitList = new ArrayList<RevCommit>()
			commitList.add(newestCommit)
			var currentTo = newestCommit
			while (!reachedOldVersion) {
				val nextCommitId = getNextCommit(currentTo, oldVersion).id
				val nextCommit = walk.parseCommit(nextCommitId)
				commitList.add(nextCommit)
				if (nextCommit == oldestCommit) {
					reachedOldVersion = true
				}
				currentTo = nextCommit
			}
			
			val commitIterator = commitList.reverse.iterator
			var fromCommit = commitIterator.next
			var toCommit = commitIterator.next
			val allResults = new ArrayList()
			while (toCommit != null) {
				val toCommitId = toCommit.id
				val fromCommitId = fromCommit.id
				logger.info("Git diffing from " + fromCommitId + " to " + toCommitId)
				val newTree = new CanonicalTreeParser
				newTree.reset(reader, toCommit.tree.id)
				val oldTree = new CanonicalTreeParser
				oldTree.reset(reader, fromCommit.tree.id)
				val diff = git.diff
				diff.newTree = newTree
				diff.oldTree = oldTree
				val rawDiffs = diff.call
				val renameDetector = new RenameDetector(repository)
				renameDetector.addAll(rawDiffs)
				val diffs = renameDetector.compute

				val result = diffs.map[createResult(it, fromCommitId, toCommitId)]
				val filteredResult = result.filter[r | r != null]
				allResults.addAll(filteredResult)
				fromCommit = toCommit
				toCommit = try {commitIterator.next} catch (NoSuchElementException e) {null}
			}
			
			return allResults

		} catch (Throwable e) {
			logger.fatal("Error during git extraction",e)
			throw new RuntimeException(e)
		} finally {
			git.close
			reader.close
		}
		
	}

	
	private def createResult(DiffEntry entry, ObjectId oldVersion, ObjectId newVersion) {
		var String newContent = null
		var String oldContent = null
			
		var newPath = Path.fromOSString(entry.newPath)
		var oldPath = Path.fromOSString(entry.oldPath)
		
		try {
			// Only set content if newpath is in project 
			if (projectRepoOffset.isPrefixOf(newPath)) {
				val newObjectLoader = repository.open(entry.newId.toObjectId)
				val newOutStream = new ByteArrayOutputStream()	
				newObjectLoader.copyTo(newOutStream)
				newContent = newOutStream.toString("UTF-8")
			}
		} catch (MissingObjectException e) {
			logger.info("File seems to have been removed: " + entry.oldPath)
		}
		
		try {
			// Only set content if oldpath is in project 
			if (projectRepoOffset.isPrefixOf(oldPath)) {
				val oldObjectLoader = repository.open(entry.oldId.toObjectId)
				val oldOutStream = new ByteArrayOutputStream()	
				oldObjectLoader.copyTo(oldOutStream)
				oldContent = oldOutStream.toString("UTF-8")
			}
		} catch (MissingObjectException e) {
			logger.info("File seems to have been added " + entry.newPath)
		}

		if (newContent == null && oldContent == null) {
			// Change not in project at all
			return null
		}
		
		if (newContent == null) {
			newPath = null
		}
		if (oldContent == null) {
			oldPath = null
		}

		return new ScmChangeResult(projectRepoOffset, newPath, newContent, newVersion.getName, oldPath, oldContent, oldVersion.getName)
	}
	
}